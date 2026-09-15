package com.example.aicleanphonestorage.feature.push

import io.docview.push.analytics.NotificationContent

/** 点击时锁存的小型快照；Click 与 Enter 共用，进入首页后不重新判断后台状态。 */
internal data class NotificationClickContext(
    val origin: String,
    val content: NotificationContent,
    val fromBackground: Boolean,
) {
    fun properties(): Map<String, Any> = mapOf(
        "Notific_Type" to when (origin) { "resident" -> 4; "remote" -> 3; else -> 1 },
        "Notific_Position" to if (origin == "resident") 2 else 1,
        "event_id" to if (origin == "resident") "permanent" else "customer_general_style",
    ) + content.properties(fromBackground)

    companion object {
        val origins = setOf("resident", "local", "remote")
        fun create(origin: String?, title: String?, text: String?, fromBackground: Boolean) =
            origin?.takeIf { it in origins }?.let {
                NotificationClickContext(it, NotificationContent(title.orEmpty(), text.orEmpty()).bounded(), fromBackground)
            }
    }
}
