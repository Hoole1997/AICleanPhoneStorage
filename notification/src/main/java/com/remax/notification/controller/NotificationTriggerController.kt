package com.remax.notification.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.remax.notification.NotificationDestination
import com.remax.notification.NotificationHost
import com.remax.notification.PushMessage

/** 提取原模块的双通道与 RemoteViews 发布职责；不启动 Service，不反复重发已展示通知。 */
internal class NotificationTriggerController(
    private val context: Context,
    private val host: NotificationHost,
) {
    fun channels() {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(RESIDENT_CHANNEL, host.residentChannelName, NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false); setSound(null, null); enableVibration(false) },
                NotificationChannel(PUSH_CHANNEL, host.pushChannelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    fun permitted(channel: String): Boolean {
        return com.remax.notification.NotificationPermissionAccess.isGranted(context, channel)
    }

    fun resident(): Boolean {
        channels()
        return post(RESIDENT_ID, RESIDENT_CHANNEL,
            NotificationCompat.Builder(context, RESIDENT_CHANNEL)
                .setSmallIcon(host.smallIcon)
                .setContentTitle(host.appName)
                .setContentIntent(host.contentIntent(NotificationDestination.HOME))
                .setCustomContentView(host.residentViews(compact = true))
                .setCustomBigContentView(host.residentViews(compact = false))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
                .setSilent(true).setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        )
    }

    fun push(message: PushMessage): Boolean {
        channels()
        return post(PUSH_ID, PUSH_CHANNEL,
            NotificationCompat.Builder(context, PUSH_CHANNEL)
                .setSmallIcon(host.smallIcon)
                .setContentTitle(message.title.take(120))
                .setContentText(message.body.take(500))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body.take(500)))
                .setContentIntent(host.contentIntent(message.destination))
                .setAutoCancel(true)
        )
    }

    @Suppress("MissingPermission") // 每次发送前再次检查权限，同时处理检查后撤权的竞态。
    private fun post(id: Int, channel: String, builder: NotificationCompat.Builder): Boolean {
        if (!permitted(channel)) return false
        return try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        } catch (_: SecurityException) { false }
    }

    companion object {
        const val RESIDENT_CHANNEL = "clean_shortcuts"
        const val PUSH_CHANNEL = "clean_reminders"
        const val RESIDENT_ID = 4101
        const val PUSH_ID = 4102
    }
}
