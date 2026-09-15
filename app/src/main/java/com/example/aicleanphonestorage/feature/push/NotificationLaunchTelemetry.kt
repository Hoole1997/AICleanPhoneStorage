package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent

/** 只携带来源枚举；冷启动点击先入有界队列，SDK 就绪后再发送，不保存通知正文。 */
internal object NotificationLaunchTelemetry {
    const val ORIGIN = "notification.metrics.origin"
    private val origins = setOf("resident", "local", "remote")

    fun origin(intent: Intent): String? {
        intent.getStringExtra(ORIGIN)?.takeIf { it in origins }?.let { return it }
        // 兼容升级前已投递的常驻卡片，它们只有既有 action 与目的地字段。
        if (intent.hasExtra(NotificationNavigation.EXTRA_DESTINATION) &&
            (intent.action?.contains(".resident.") == true || intent.action?.contains(".notification.") == true)) return "resident"
        if (intent.hasExtra("google.message_id") || intent.hasExtra("gcm.message_id")) return "remote"
        if (io.docview.push.controller.LandingCtrl.isFromNotification(intent)) {
            return if (intent.getStringExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM) in setOf("fcm", "firebase_push")) "remote" else "local"
        }
        return null
    }

    fun clicked(origin: String?, sink: EventSink = BusinessTelemetry) {
        properties(origin)?.let { sink.send(MetricEvent.NOTIFICATION_CLICK, it) }
    }

    fun entered(intent: Intent, sink: EventSink = BusinessTelemetry) {
        val origin = intent.getStringExtra(ORIGIN)
        intent.removeExtra(ORIGIN) // 同一首页 Intent 旋转/恢复不重复上报进入。
        properties(origin)?.let { sink.send(MetricEvent.NOTIFICATION_ENTER, it) }
    }

    private fun properties(origin: String?): Map<String, Any>? {
        if (origin !in origins) return null
        return mapOf(
            "Notific_Type" to when (origin) { "resident" -> 4; "remote" -> 3; else -> 1 },
            "Notific_Position" to if (origin == "resident") 2 else 1,
            "event_id" to if (origin == "resident") "permanent" else "customer_general_style",
        )
    }
}
