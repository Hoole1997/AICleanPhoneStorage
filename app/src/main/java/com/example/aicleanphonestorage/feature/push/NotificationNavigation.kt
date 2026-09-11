package com.example.aicleanphonestorage.feature.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.feature.startup.StartupActivity
import io.docview.push.NotificationDestination

/** 通知先直接投递到启动 Activity，再由首页执行权限与业务入口；没有广播/Service 中转。 */
internal object NotificationNavigation {
    const val EXTRA_DESTINATION = "notification.destination"

    fun pendingIntent(context: Context, destination: NotificationDestination): PendingIntent =
        PendingIntent.getActivity(context, 4100 + destination.contentType,
            Intent(context, StartupActivity::class.java)
                .setAction("${context.packageName}.notification.${destination.key}")
                .putExtra(EXTRA_DESTINATION, destination.key)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun residentPendingIntent(context: Context, destination: NotificationDestination, entry: String, badge: String): PendingIntent =
        PendingIntent.getActivity(context, 6100 + destination.contentType,
            Intent(context, StartupActivity::class.java)
                .setAction("${context.packageName}.resident.$entry.$badge")
                .putExtra(EXTRA_DESTINATION, destination.key)
                .putExtra(ResidentClickTelemetry.ENTRY, entry)
                .putExtra(ResidentClickTelemetry.BADGE, badge)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun read(intent: Intent): NotificationDestination? = when {
        intent.hasExtra(EXTRA_DESTINATION) -> NotificationDestination.fromKey(intent.getStringExtra(EXTRA_DESTINATION))
        io.docview.push.controller.LandingCtrl.isFromNotification(intent) -> NotificationDestination.fromContentType(
            io.docview.push.controller.LandingCtrl.getNotificationActionType(intent))
        // Firebase Console 的系统展示通知也走 Launcher；仅接受白名单目标。
        intent.hasExtra("google.message_id") || intent.hasExtra("gcm.message_id") ->
            NotificationDestination.fromKey(intent.getStringExtra("destination"))
        else -> null
    }

    fun consume(intent: Intent): NotificationDestination? {
        val destination = read(intent) ?: return null
        intent.removeExtra(EXTRA_DESTINATION)
        intent.removeExtra("google.message_id")
        intent.removeExtra("gcm.message_id")
        intent.removeExtra("destination")
        intent.removeExtra(com.example.aicleanphonestorage.feature.startup.StartupNavigation.COMPLETED)
        if (io.docview.push.controller.LandingCtrl.isFromNotification(intent))
            io.docview.push.controller.LandingCtrl.clearNotificationParameters(intent)
        return destination
    }
}
