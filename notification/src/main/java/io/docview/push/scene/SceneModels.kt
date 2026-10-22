package io.docview.push.scene

import io.docview.push.NotificationDestination
import io.docview.push.R
import io.docview.push.analytics.NotificationTriggerSource

/** 固定触发确认统一一分钟；不用于远程推送间隔、充电防抖或循环展示窗口。 */
internal const val SCENE_CONFIRMATION_DELAY_MS = 60_000L

/** 编号、图标、落地页与频控键集中定义；不进入旧日龄文案池/CheckCtrl 的全局计数。 */
enum class PushScene(val code: Int, val intervalKey: String, val destination: NotificationDestination,
    val source: NotificationTriggerSource, val icon: Int, val label: Int) {
    CHARGE_START(1101, "chargeStartPushIntervalMinutes", NotificationDestination.BATTERY, NotificationTriggerSource.CHARGE_START, R.drawable.scene_charging_start, R.string.scene_charge_start),
    CHARGE_END(1102, "chargeCompletePushIntervalMinutes", NotificationDestination.BATTERY, NotificationTriggerSource.CHARGE_END, R.drawable.scene_charging_end, R.string.scene_charge_end),
    LOW_BATTERY(1103, "lowBatteryPushIntervalMinutes", NotificationDestination.BATTERY, NotificationTriggerSource.LOW_BATTERY, R.drawable.scene_low_battery, R.string.scene_low_battery),
    NETWORK(1104, "networkChangePushIntervalMinutes", NotificationDestination.NETWORK, NotificationTriggerSource.NETWORK_CHANGE, R.drawable.scene_network, R.string.scene_network),
    SCREENSHOT(1105, "screenshotPushIntervalMinutes", NotificationDestination.SCREENSHOTS, NotificationTriggerSource.SCREENSHOT, R.drawable.scene_screenshot, R.string.scene_screenshot),
    APP_INSTALL(1106, "appInstallPushIntervalMinutes", NotificationDestination.APP_MANAGER, NotificationTriggerSource.APP_INSTALL, R.drawable.scene_app_installed, R.string.scene_app_install),
    APP_UNINSTALL(1107, "appUninstallPushIntervalMinutes", NotificationDestination.CLEAN, NotificationTriggerSource.APP_UNINSTALL, R.drawable.scene_app_uninstalled, R.string.scene_app_uninstall),
    DOWNLOAD(1108, "newDownloadPushIntervalMinutes", NotificationDestination.LARGE_FILES, NotificationTriggerSource.NEW_DOWNLOAD, R.drawable.scene_download, R.string.scene_download);

    val notificationId get() = 12_000 + code
    /** 日志固定中文，独立于用户界面语言；不输出事件里的文件路径、包名或文案。 */
    internal val logName get() = when (this) {
        CHARGE_START -> "充电开始"
        CHARGE_END -> "充电结束"
        LOW_BATTERY -> "低电量"
        NETWORK -> "网络变化"
        SCREENSHOT -> "用户截图"
        APP_INSTALL -> "应用安装"
        APP_UNINSTALL -> "应用卸载"
        DOWNLOAD -> "新增下载"
    } + "($code)"
    companion object { fun fromCode(code: Int) = entries.firstOrNull { it.code == code } }
}

internal enum class SceneNetwork { UNKNOWN, OFFLINE, WIFI, MOBILE, OTHER, VPN }

/** 只有有界元数据，不读图片/APK/文档内容；延迟任务最多每个场景一个。 */
internal data class SceneEvent(
    val scene: PushScene,
    val occurredAt: Long,
    val dueElapsed: Long,
    val reference: String = "",
    val displayName: String = "",
    val bytes: Long = 0,
    val modified: Long = 0,
    val batteryPercent: Int? = null,
    val fullCharge: Boolean = false,
    val charging: Boolean? = null,
    val plugged: Boolean? = null,
    val chargeDurationMs: Long? = null,
    val network: SceneNetwork = SceneNetwork.UNKNOWN,
    val fileCount: Int = 1,
) {
    val identity get() = "${scene.code}:$reference:$occurredAt:$modified:$bytes"
}

internal data class SceneMoment(val wall: Long, val elapsed: Long, val boot: Int)
internal data class SceneHistory(val wall: Long = 0, val elapsed: Long = 0, val boot: Int = -1, val copyId: String = "") {
    fun remaining(now: SceneMoment, intervalMs: Long): Long {
        if (copyId.isEmpty()) return 0
        val age = if (boot >= 0 && boot == now.boot && now.elapsed >= elapsed) now.elapsed - elapsed else now.wall - wall
        // 同一开机周期使用单调时钟；跨开机遇到时钟回拨最多再等待一个配置间隔。
        return (intervalMs - age.coerceAtLeast(0)).coerceAtLeast(0)
    }
}

object ScenePushKeys {
    const val CONFIG = "scene_push_config"
    const val CONTENT = "scene_push_content"
}

internal data class SceneRule(val enabled: Boolean = true, val intervalMinutes: Int = 10) {
    val intervalMs get() = intervalMinutes.toLong() * 60_000L
}
internal data class ScenePolicy(val enabled: Boolean = true, val rules: Map<PushScene, SceneRule> = PushScene.entries.associateWith { SceneRule() })
internal data class SceneChoices(val enabled: Boolean = true, val disabled: Set<PushScene> = emptySet())
internal data class SceneConfiguration(val policy: ScenePolicy, val copies: Map<PushScene, List<SceneCopy>>, val choices: SceneChoices) {
    fun enabled(scene: PushScene) = policy.enabled && choices.enabled && scene !in choices.disabled && policy.rules.getValue(scene).enabled
}
