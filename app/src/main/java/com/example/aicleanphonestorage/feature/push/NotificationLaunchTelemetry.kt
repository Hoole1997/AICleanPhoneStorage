package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import io.docview.push.analytics.NotificationContentIntent
import io.docview.push.analytics.NotificationTriggerSource
import io.docview.push.analytics.NotificationVisibility

/** 自家通知的有界文案与来源通过白名单传递；不读取其他 App 通知，也不复制完整 extras。 */
internal object NotificationLaunchTelemetry {
    const val ORIGIN = "notification.metrics.origin"
    const val FROM_BACKGROUND = "notification.metrics.from_background"
    const val TRIGGER_SOURCE = NotificationTriggerSource.EXTRA
    const val ENTRY_CONSUMED = "notification.metrics.entry_consumed"

    fun origin(intent: Intent): String? {
        intent.getStringExtra(ORIGIN)?.takeIf { it in NotificationClickContext.origins }?.let { return it }
        if (intent.hasExtra(NotificationNavigation.EXTRA_DESTINATION) &&
            (intent.action?.contains(".resident.") == true || intent.action?.contains(".notification.") == true)) return "resident"
        if (intent.hasExtra("google.message_id") || intent.hasExtra("gcm.message_id")) return "remote"
        if (io.docview.push.controller.LandingCtrl.isFromNotification(intent)) {
            return when (intent.getStringExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM)) {
                "fcm", "firebase_push" -> "remote"
                else -> "local"
            }
        }
        return null
    }

    fun read(intent: Intent, fromBackground: Boolean = NotificationVisibility.backgroundForClick()): NotificationClickContext? {
        if (intent.getBooleanExtra(ENTRY_CONSUMED, false)) return null
        val source = origin(intent) ?: return null
        val content = NotificationContentIntent.read(intent)
        val background = if (intent.hasExtra(FROM_BACKGROUND)) intent.getBooleanExtra(FROM_BACKGROUND, fromBackground) else fromBackground
        // 新通知在构建时锁存准确来源；旧 local_push 不包含足够信息，不能补成某一种本地触发。
        // 常驻/FCM 旧 Intent 由 create 根据明确类别兼容，Notific_Type 仍按原类别计算。
        val trigger = if (intent.hasExtra(TRIGGER_SOURCE)) intent.getStringExtra(TRIGGER_SOURCE)
            else when (val legacy = intent.getStringExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM)) {
                // 兼容旧来源时只补来源字段，不改变旧 origin / Notific_Type 的归类规则。
                "top_notification" -> NotificationTriggerSource.RESIDENT.value
                else -> legacy
            }
        return NotificationClickContext.create(source, content.title, content.text, background, trigger)
    }

    fun write(intent: Intent, snapshot: NotificationClickContext?) {
        clear(intent)
        intent.removeExtra(ENTRY_CONSUMED)
        if (snapshot == null) return
        intent.putExtra(ORIGIN, snapshot.origin).putExtra(FROM_BACKGROUND, snapshot.fromBackground)
        snapshot.triggerSource?.let { intent.putExtra(TRIGGER_SOURCE, it) }
        NotificationContentIntent.write(intent, snapshot.content)
    }

    /** 入口已接收到通知点击即算进入，不等待权限、广告或首页。调用方先消费快照，再调用此方法。 */
    fun entered(snapshot: NotificationClickContext?, sink: EventSink = BusinessTelemetry) {
        snapshot ?: return
        val properties = snapshot.properties()
        sink.send(MetricEvent.NOTIFICATION_CLICK, properties)
        sink.send(MetricEvent.NOTIFICATION_ENTER, properties)
    }

    fun entered(intent: Intent, sink: EventSink = BusinessTelemetry) {
        // 首页兜底也兼容直接到达的旧通知/FCM Intent；标记阻止原始来源字段被再次识别。
        val snapshot = read(intent) ?: return
        intent.putExtra(ENTRY_CONSUMED, true)
        clear(intent)
        entered(snapshot, sink)
    }

    private fun clear(intent: Intent) {
        intent.removeExtra(ORIGIN)
        intent.removeExtra(FROM_BACKGROUND)
        intent.removeExtra(TRIGGER_SOURCE)
        NotificationContentIntent.clear(intent)
    }
}
