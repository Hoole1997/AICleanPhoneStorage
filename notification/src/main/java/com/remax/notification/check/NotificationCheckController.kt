package com.remax.notification.check

import com.remax.notification.config.PushConfig
import java.time.LocalTime
import java.time.ZonedDateTime

enum class NotificationType { UNLOCK, BACKGROUND, FCM }

/** 从原检查控制器提取为纯策略，可测试跨午夜、时钟回拨和版本过滤。常驻栏不占推送额度。 */
internal object NotificationCheckController {
    fun allowed(
        config: PushConfig,
        type: NotificationType,
        now: ZonedDateTime,
        installedAt: Long,
        foreground: Boolean,
        count: Int,
        lastSent: Long,
    ): Boolean {
        if (!config.notificationEnabled || foreground || count >= config.totalPushCount) return false
        val epoch = now.toInstant().toEpochMilli()
        if (epoch - installedAt < config.newUserCooldown * 60_000L) return false
        if (quiet(now.toLocalTime(), config.doNotDisturbStart, config.doNotDisturbEnd)) return false
        val interval = when (type) {
            NotificationType.UNLOCK -> config.unlockPushInterval
            NotificationType.BACKGROUND -> config.backgroundPushInterval
            NotificationType.FCM -> 0
        } * 60_000L
        return lastSent == 0L || epoch - lastSent >= interval
    }

    fun quiet(now: LocalTime, start: String, end: String): Boolean = try {
        val from = LocalTime.parse(start)
        val until = LocalTime.parse(end)
        when {
            from == until -> false
            from < until -> now >= from && now < until
            else -> now >= from || now < until
        }
    } catch (_: java.time.format.DateTimeParseException) { true }

    fun versionMatches(messageVersion: String?, currentVersion: String) =
        messageVersion.isNullOrBlank() || messageVersion == currentVersion
}
