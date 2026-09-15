package io.docview.push.analytics

import android.content.Intent
import io.docview.push.builder.LANDING_NOTIFICATION_CONTENT
import io.docview.push.builder.LANDING_NOTIFICATION_TITLE

/** 仅在已识别的自家通知入口读取；原始外部 extras 不继续转发。 */
object NotificationContentIntent {
    const val TITLE = "notification.metrics.title"
    const val TEXT = "notification.metrics.text"

    fun write(intent: Intent, content: NotificationContent) {
        val safe = content.bounded()
        intent.putExtra(TITLE, safe.title).putExtra(TEXT, safe.text)
    }

    fun read(intent: Intent): NotificationContent = NotificationContent(
        intent.getStringExtra(TITLE) ?: intent.getStringExtra(LANDING_NOTIFICATION_TITLE)
            ?: intent.getStringExtra("gcm.n.title") ?: intent.getStringExtra("title").orEmpty(),
        intent.getStringExtra(TEXT) ?: intent.getStringExtra(LANDING_NOTIFICATION_CONTENT)
            ?: intent.getStringExtra("gcm.n.body") ?: intent.getStringExtra("text")
            ?: intent.getStringExtra("body").orEmpty(),
    ).bounded()

    fun clear(intent: Intent) {
        intent.removeExtra(TITLE)
        intent.removeExtra(TEXT)
    }
}
