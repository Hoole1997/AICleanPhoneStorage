package io.docview.push.scene

internal data class SceneBattery(val percent: Int?, val plugged: Boolean, val charging: Boolean, val full: Boolean)
internal data class SceneSignals(val events: List<SceneEvent> = emptyList(), val cancel: Set<PushScene> = emptySet())

/** 纯状态边沿判定，首次粘性快照只建立基线，不把前台服务重建当成插拔/低电量事件。 */
internal class BatterySceneRules {
    var current: SceneBattery? = null
        private set
    private var startedElapsed: Long? = null
    private var session = ""
    private var lastConnection: Long? = null
    private var ended = false
    private var low20 = false
    private var low10 = false

    fun update(value: SceneBattery, now: SceneMoment, lowBroadcast: Boolean = false, okayBroadcast: Boolean = false): SceneSignals {
        val previous = current
        current = value
        if (previous == null) { session = now.elapsed.toString(); return SceneSignals() }
        val events = mutableListOf<SceneEvent>()
        val cancel = mutableSetOf<PushScene>()
        if (value.plugged || value.charging || okayBroadcast || (value.percent ?: 0) > 20) {
            low20 = false; low10 = false; cancel += PushScene.LOW_BATTERY
        }
        val connected = !previous.plugged && value.plugged
        if (connected || (!previous.charging && value.charging && !previous.full)) {
            cancel += PushScene.CHARGE_END
            // 同一次连接可能先报告 plugged，再报告 charging（也可能反过来）。
            // 后续状态补全不能重建会话，否则防抖虽不生成新事件，却会让旧候选的 reference 失效。
            val ongoing = startedElapsed != null && !ended && (previous.plugged || previous.charging)
            if (!ongoing) {
                session = now.elapsed.toString(); startedElapsed = now.elapsed; ended = false
                val bounce = lastConnection?.let { now.elapsed - it < 300_000L } == true
                lastConnection = now.elapsed
                if (!bounce) events += SceneEvent(PushScene.CHARGE_START, now.wall, now.elapsed,
                    reference = session, batteryPercent = value.percent, charging = value.charging, plugged = value.plugged, fullCharge = value.full)
            }
        }
        if ((previous.plugged && !value.plugged) || (previous.charging && !value.charging)) {
            cancel += PushScene.CHARGE_START
            val duration = startedElapsed?.let { (now.elapsed - it).coerceAtLeast(0) }
            if (!ended && (duration == null || duration >= 60_000L)) {
                ended = true
                events += SceneEvent(PushScene.CHARGE_END, now.wall, now.elapsed + SCENE_CONFIRMATION_DELAY_MS,
                    reference = session, batteryPercent = value.percent, fullCharge = previous.full || value.full || value.percent == 100,
                    charging = value.charging, plugged = value.plugged,
                    chargeDurationMs = duration)
            }
        }
        val percent = value.percent
        if (!value.plugged && !value.charging && !okayBroadcast && percent != null) {
            val enteringLow = lowBroadcast || previous.plugged || (previous.percent ?: percent) > 20
            val enteringCritical = (previous.percent ?: percent) > 10 || lowBroadcast || previous.plugged
            if (percent <= 10 && !low10 && enteringCritical) {
                low10 = true; low20 = true
                events += SceneEvent(PushScene.LOW_BATTERY, now.wall, now.elapsed, batteryPercent = percent)
            } else if (percent <= 20 && !low20 && enteringLow) {
                low20 = true
                events += SceneEvent(PushScene.LOW_BATTERY, now.wall, now.elapsed, batteryPercent = percent)
            }
        }
        return SceneSignals(events, cancel)
    }

    fun valid(event: SceneEvent): Boolean {
        val battery = current ?: return false
        return when (event.scene) {
            PushScene.CHARGE_START -> event.reference == session && (battery.charging || battery.plugged)
            PushScene.CHARGE_END -> event.reference == session && !battery.charging
            PushScene.LOW_BATTERY -> !battery.plugged && !battery.charging && battery.percent?.let { it <= 20 } == true
            else -> false
        }
    }
}

internal data class SceneNetworkState(val kind: SceneNetwork, val identity: String = "")
internal class NetworkSceneRules(initial: SceneNetworkState) {
    var current = initial
        private set
    private var wifiLeftAt: Long? = null
    private var vpnGraceUntil = -1L
    private var lastConnectedKind = initial.kind
    fun update(next: SceneNetworkState, now: SceneMoment): SceneSignals {
        val before = current
        if (before == next) return SceneSignals()
        // 部分设备先回调旧 Wi-Fi 的 onLost，再回调蜂窝网络；保留断线前类型，避免漏报切换。
        val origin = if (before.kind == SceneNetwork.OFFLINE) lastConnectedKind else before.kind
        if (next.kind != SceneNetwork.OFFLINE && next.kind != SceneNetwork.UNKNOWN) lastConnectedKind = next.kind
        current = next
        val cancel = setOf(PushScene.NETWORK)
        if (before.kind == SceneNetwork.WIFI && next.kind != SceneNetwork.WIFI) wifiLeftAt = now.elapsed
        if (next.kind == SceneNetwork.VPN) { vpnGraceUntil = Long.MAX_VALUE; return SceneSignals(cancel = cancel) }
        if (before.kind == SceneNetwork.VPN) vpnGraceUntil = now.elapsed + 120_000L
        if (before.kind == SceneNetwork.UNKNOWN || next.kind == SceneNetwork.UNKNOWN)
            return SceneSignals(cancel = cancel)
        // VPN 断开、Wi-Fi 漫游有时经过短暂 onLost。没有 SSID 证据时保守跳过快速回连，
        // 真实持续离线按统一一分钟确认，不使用探测请求或位置权限猜测网络。
        if (next.kind != SceneNetwork.OFFLINE && now.elapsed <= vpnGraceUntil) {
            vpnGraceUntil = -1L
            return SceneSignals(cancel = cancel)
        }
        if (next.kind == SceneNetwork.WIFI && wifiLeftAt?.let { now.elapsed - it < 120_000L } == true)
            return SceneSignals(cancel = cancel)
        val eligible = next.kind == SceneNetwork.OFFLINE ||
            (origin == SceneNetwork.WIFI && next.kind == SceneNetwork.MOBILE) ||
            (next.kind == SceneNetwork.WIFI && before.kind != SceneNetwork.WIFI)
        // 不请求 SSID/BSSID/位置权限；Wi-Fi→Wi-Fi 无法可靠区分漫游时跳过，不能猜测“新 Wi-Fi”。
        if (!eligible) return SceneSignals(cancel = cancel)
        return SceneSignals(listOf(SceneEvent(PushScene.NETWORK, now.wall, now.elapsed + SCENE_CONFIRMATION_DELAY_MS,
            reference = next.identity, network = next.kind)), cancel)
    }
    fun valid(event: SceneEvent) = event.network == current.kind && event.reference == current.identity
}

/** 截图识别后立即交给场景间隔与队列；无 URI 去重状态，也无额外五分钟等待。 */
internal object ScreenshotSceneRules {
    fun onMediaChanged(newestFirst: List<SceneMedia>, now: SceneMoment): SceneEvent? {
        // 同一批回调只提交最新截图候选，避免对查询返回的历史行逐一重复调度。
        // 后续即使仍是相同 URI，也重新交由远程间隔判定，不在此过滤。
        val latest = newestFirst.firstOrNull() ?: return null
        return SceneEvent(PushScene.SCREENSHOT, latest.added * 1000, now.elapsed, latest.uri,
            bytes = latest.bytes, modified = latest.modified)
    }
}

/** 下载只保存最近 30 分钟内最多 256 份元数据；普通小文件聚合，不为每个小文件弹通知。 */
internal class DownloadSceneBatch {
    private val recent = ArrayDeque<SceneEvent>()
    fun add(event: SceneEvent): SceneEvent? {
        while (recent.firstOrNull()?.let { event.occurredAt - it.occurredAt > 1_800_000L } == true) recent.removeFirst()
        if (recent.any { it.reference == event.reference }) return null
        recent.addLast(event)
        while (recent.size > 256) recent.removeFirst()
        val bytes = recent.fold(0L) { sum, file -> if (Long.MAX_VALUE - sum < file.bytes) Long.MAX_VALUE else sum + file.bytes }
        if (event.bytes <= 100_000_000L && bytes <= 200_000_000L && recent.size < 2) return null
        return event.copy(fileCount = recent.size)
    }
    fun clear() = recent.clear()
}
