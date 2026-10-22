package io.docview.push.analytics

/**
 * 稳定的通知触发来源协议，与旧 Notific_Type 和落地路由字符串独立。
 * 展示和点击共用白名单，避免外部 Intent 的任意字符串进入埋点。
 */
enum class NotificationTriggerSource(val value: String) {
    UNLOCK("unlock"),
    BACKGROUND("background"),
    KEEPALIVE("keepalive"),
    ALARM_MANAGER("alarmmanager"),
    FCM("fcm"),
    RESIDENT("resident"),
    CHARGE_START("charge_start"), CHARGE_END("charge_end"), LOW_BATTERY("low_battery"),
    NETWORK_CHANGE("network_change"), SCREENSHOT("screenshot"), APP_INSTALL("app_install"),
    APP_UNINSTALL("app_uninstall"), NEW_DOWNLOAD("new_download");

    fun properties(): Map<String, Any> = mapOf("trigger_source" to value)

    companion object {
        const val EXTRA = "notification.metrics.trigger_source"

        fun fromValue(value: String?): NotificationTriggerSource? =
            entries.firstOrNull { it.value == value }
    }
}
