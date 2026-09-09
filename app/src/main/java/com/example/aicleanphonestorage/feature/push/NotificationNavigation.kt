package com.example.aicleanphonestorage.feature.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.app.MainActivity
import io.docview.push.NotificationDestination

/** 所有通知直接投递到首页 Activity，经相同权限入口启动功能，避免 notification trampoline。 */
internal object NotificationNavigation {
    const val EXTRA_DESTINATION = "notification.destination"

    fun pendingIntent(context: Context, destination: NotificationDestination): PendingIntent =
        PendingIntent.getActivity(context, 4100 + destination.contentType,
            Intent(context, MainActivity::class.java)
                .setAction("${context.packageName}.notification.${destination.key}")
                .putExtra(EXTRA_DESTINATION, destination.key)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun consume(intent: Intent): NotificationDestination? {
        if (!intent.hasExtra(EXTRA_DESTINATION)) {
            if (!io.docview.push.controller.LandingCtrl.isFromNotification(intent)) return null
            val destination = NotificationDestination.fromContentType(
                io.docview.push.controller.LandingCtrl.getNotificationActionType(intent))
            io.docview.push.controller.LandingCtrl.clearNotificationParameters(intent)
            return destination
        }
        val destination = NotificationDestination.fromKey(intent.getStringExtra(EXTRA_DESTINATION))
        // 消费标记，旋转/语言重建不重复启动扫描；新点击由 onNewIntent 再次送达。
        intent.removeExtra(EXTRA_DESTINATION)
        return destination
    }
}
