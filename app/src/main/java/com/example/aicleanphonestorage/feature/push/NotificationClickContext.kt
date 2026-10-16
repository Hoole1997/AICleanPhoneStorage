package com.example.aicleanphonestorage.feature.push

import io.docview.push.analytics.NotificationContent
import io.docview.push.analytics.NotificationTriggerSource

/** 点击时锁存的小型快照；Click 与 Enter 在首个入口共用，不在后续页面重新判断后台状态。 */
internal data class NotificationClickContext(
    val origin: String,
    val content: NotificationContent,
    val fromBackground: Boolean,
    val triggerSource: String? = null,
) {
    fun properties(): Map<String, Any> = mapOf(
        "Notific_Type" to when (origin) { "resident" -> 4; "remote" -> 3; else -> 1 },
        "Notific_Position" to if (origin == "resident") 2 else 1,
        "event_id" to if (origin == "resident") "permanent" else "customer_general_style",
    ) + content.properties(fromBackground) + triggerProperties()

    private fun triggerProperties(): Map<String, Any> =
        NotificationTriggerSource.fromValue(triggerSource)?.properties().orEmpty()

    companion object {
        val origins = setOf("resident", "local", "remote")
        fun create(
            origin: String?,
            title: String?,
            text: String?,
            fromBackground: Boolean,
            triggerSource: String? = null,
        ) =
            origin?.takeIf { it in origins }?.let {
                NotificationClickContext(
                    it,
                    NotificationContent(title.orEmpty(), text.orEmpty()).bounded(),
                    fromBackground,
                    resolveTriggerSource(it, triggerSource)?.value,
                )
            }

        private fun resolveTriggerSource(origin: String, value: String?): NotificationTriggerSource? {
            NotificationTriggerSource.fromValue(value)?.let { return it }
            // 老版本常驻/FCM 的类别足以确认来源；local_push 无法细分，不能猜成解锁。
            return when (origin) {
                "resident" -> NotificationTriggerSource.RESIDENT
                "remote" -> NotificationTriggerSource.FCM
                else -> null
            }
        }
    }
}
