package io.docview.push.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi

/** 声音/震动由系统通道管理；版本升级只安装新默认值，不反复覆盖已创建通道的用户设置。 */
object GeneralNotificationChannels {
    const val ALERTING = "general_notification_v2"
    const val LEGACY = "general_notification"

    @RequiresApi(26)
    fun alerting(name: String, legacy: NotificationChannel?): NotificationChannel {
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // 旧版应用默认禁止震动。仅未改动旧默认行为时升级为声+震；已知用户选择保持原样。
        val customized = legacy != null && (
            legacy.importance != NotificationManager.IMPORTANCE_HIGH || legacy.sound != defaultSound ||
                legacy.shouldVibrate() || legacy.vibrationPattern != null ||
                (Build.VERSION.SDK_INT >= 29 && legacy.hasUserSetImportance()) ||
                (Build.VERSION.SDK_INT >= 30 && legacy.hasUserSetSound()))
        return NotificationChannel(ALERTING, name, legacy?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
            description = "for general notification"
            setShowBadge(legacy?.canShowBadge() ?: true)
            enableLights(legacy?.shouldShowLights() ?: false)
            legacy?.let {
                lockscreenVisibility = it.lockscreenVisibility
                lightColor = it.lightColor
            }
            setSound(if (legacy == null) defaultSound else legacy.sound,
                legacy?.audioAttributes ?: AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            if (customized && legacy != null) {
                vibrationPattern = legacy.vibrationPattern
                enableVibration(legacy.shouldVibrate())
            } else enableVibration(true)
        }
    }
}
