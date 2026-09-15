package com.example.aicleanphonestorage.feature.push

import androidx.lifecycle.SavedStateHandle
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent

internal enum class PushPermissionPosition(val wire: String) {
    HOME("HomeScreen"), SPLASH("SplashScreen"),
}

internal enum class PushPermissionOutcome(val wire: String?, val granted: Boolean = false) {
    ALLOWED("allow", true), DENIED("denied"),
    // 按埋点协议保留 deined 的既有拼写，不擅自改成 denied_forever。
    DENIED_FOREVER("deined_forever"), ALREADY_ALLOWED("allow1", true), UNAVAILABLE(null),
}

internal enum class PushPermissionRequestMode { RUNTIME, SETTINGS }

/** 每个宿主授权流程的事件账本；只存基本类型，旋转保留，旧回调不能冒充新请求的结果。 */
internal class PushPermissionTelemetry(private val saved: SavedStateHandle) {
    init {
        // 新 ViewModel 意味着运行时 SDK 回调已丢失；设置页结果由共享 ActivityResult 流程恢复。
        if (saved.get<String>(MODE) == PushPermissionRequestMode.RUNTIME.name) clearActive()
    }

    fun alreadyGranted(position: PushPermissionPosition, sink: EventSink) {
        if (saved.get<Boolean>(OBSERVED) == true || saved.get<Long>(ACTIVE) != null) return
        saved[OBSERVED] = true
        result(position.wire, PushPermissionOutcome.ALREADY_ALLOWED, sink)
    }

    fun started(position: PushPermissionPosition, mode: PushPermissionRequestMode, sink: EventSink): Long {
        saved.get<Long>(ACTIVE)?.let { return it }
        val id = (saved.get<Long>(SEQUENCE) ?: 0) + 1
        saved[SEQUENCE] = id
        saved[ACTIVE] = id
        saved[POSITION] = position.wire
        saved[MODE] = mode.name
        saved[OBSERVED] = true
        sink.send(MetricEvent.NOTIFICATION_ALLOW_START, mapOf("Notific_Allow_Position" to position.wire))
        return id
    }

    fun active(mode: PushPermissionRequestMode): Long? =
        saved.get<Long>(ACTIVE)?.takeIf { saved.get<String>(MODE) == mode.name }

    fun completed(id: Long, outcome: PushPermissionOutcome, sink: EventSink): Boolean {
        if (saved.get<Long>(ACTIVE) != id) return false
        val position = saved.get<String>(POSITION) ?: return false
        clearActive()
        outcome.wire?.let { result(position, outcome, sink) }
        return true
    }

    fun reset() {
        clearActive()
        saved[OBSERVED] = false
        // 序号不归零，避免下一次流程复用旧回调的 token。
    }

    private fun result(position: String, outcome: PushPermissionOutcome, sink: EventSink) {
        sink.send(MetricEvent.NOTIFICATION_ALLOW_RESULT,
            mapOf("Notific_Allow_Position" to position, "Result" to requireNotNull(outcome.wire)))
    }

    private fun clearActive() {
        saved.remove<Long>(ACTIVE)
        saved.remove<String>(MODE)
        saved.remove<String>(POSITION)
    }

    private companion object {
        const val OBSERVED = "push.metrics.observed"
        const val ACTIVE = "push.metrics.active"
        const val SEQUENCE = "push.metrics.sequence"
        const val MODE = "push.metrics.mode"
        const val POSITION = "push.metrics.position"
    }
}
