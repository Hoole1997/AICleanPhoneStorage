package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import io.docview.push.analytics.NotificationContentIntent
import io.docview.push.analytics.NotificationVisibility

/** 自家通知的有界文案与来源通过白名单传递；不读取其他 App 通知，也不复制完整 extras。 */
internal object NotificationLaunchTelemetry {
    const val ORIGIN = "notification.metrics.origin"
    const val FROM_BACKGROUND = "notification.metrics.from_background"

    fun origin(intent: Intent): String? {
        intent.getStringExtra(ORIGIN)?.takeIf { it in NotificationClickContext.origins }?.let { return it }
        if (intent.hasExtra(NotificationNavigation.EXTRA_DESTINATION) &&
            (intent.action?.contains(".resident.") == true || intent.action?.contains(".notification.") == true)) return "resident"
        if (intent.hasExtra("google.message_id") || intent.hasExtra("gcm.message_id")) return "remote"
        if (io.docview.push.controller.LandingCtrl.isFromNotification(intent)) {
            return if (intent.getStringExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM) in setOf("fcm", "firebase_push")) "remote" else "local"
        }
        return null
    }

    fun read(intent: Intent, fromBackground: Boolean = NotificationVisibility.backgroundForClick()): NotificationClickContext? {
        val source = origin(intent) ?: return null
        val content = NotificationContentIntent.read(intent)
        val background = if (intent.hasExtra(FROM_BACKGROUND)) intent.getBooleanExtra(FROM_BACKGROUND, fromBackground) else fromBackground
        return NotificationClickContext.create(source, content.title, content.text, background)
    }

    fun write(intent: Intent, snapshot: NotificationClickContext?) {
        clear(intent)
        if (snapshot == null) return
        intent.putExtra(ORIGIN, snapshot.origin).putExtra(FROM_BACKGROUND, snapshot.fromBackground)
        NotificationContentIntent.write(intent, snapshot.content)
    }

    fun clicked(snapshot: NotificationClickContext?, sink: EventSink = BusinessTelemetry) {
        snapshot?.let { sink.send(MetricEvent.NOTIFICATION_CLICK, it.properties()) }
    }

    fun entered(intent: Intent, sink: EventSink = BusinessTelemetry) {
        // 仅消费启动页传来的规范化上下文；移除后旋转/再次调用不会通过遗留来源字段重复上报。
        if (intent.getStringExtra(ORIGIN) !in NotificationClickContext.origins) return
        val snapshot = read(intent)
        clear(intent)
        snapshot?.let { sink.send(MetricEvent.NOTIFICATION_ENTER, it.properties()) }
    }

    private fun clear(intent: Intent) {
        intent.removeExtra(ORIGIN)
        intent.removeExtra(FROM_BACKGROUND)
        NotificationContentIntent.clear(intent)
    }
}
