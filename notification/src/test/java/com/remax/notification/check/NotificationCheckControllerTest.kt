package com.remax.notification.check

import com.remax.notification.NotificationDestination
import com.remax.notification.config.PushConfig
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class NotificationCheckControllerTest {
    private val now = ZonedDateTime.parse("2026-09-09T12:00:00-04:00")
    private val epoch = now.toInstant().toEpochMilli()
    private val config = PushConfig()

    private fun allowed(type: NotificationType = NotificationType.BACKGROUND, count: Int = 0,
        last: Long = 0, installed: Long = epoch - 86_400_000, foreground: Boolean = false) =
        NotificationCheckController.allowed(config, type, now, installed, foreground, count, last)

    @Test fun frequencyBoundariesAndForegroundAreEnforced() {
        assertTrue(allowed(last = epoch - 600_000))
        assertFalse(allowed(last = epoch - 599_999))
        assertFalse(allowed(last = epoch + 1))
        assertFalse(allowed(count = 3))
        assertFalse(allowed(foreground = true))
        assertFalse(allowed(installed = epoch - 60_000))
        assertTrue(allowed(installed = epoch - 24 * 60_000))
    }

    @Test fun fcmBypassesIntervalButNotDailyBudgetOrForeground() {
        assertTrue(allowed(NotificationType.FCM, last = epoch))
        assertFalse(allowed(NotificationType.FCM, count = 3))
        assertFalse(allowed(NotificationType.FCM, foreground = true))
    }

    @Test fun quietHoursIncludeStartExcludeEndAndCrossMidnight() {
        assertTrue(NotificationCheckController.quiet(LocalTime.of(2, 0), "02:00", "08:00"))
        assertFalse(NotificationCheckController.quiet(LocalTime.of(8, 0), "02:00", "08:00"))
        assertTrue(NotificationCheckController.quiet(LocalTime.of(23, 0), "22:00", "08:00"))
        assertTrue(NotificationCheckController.quiet(LocalTime.of(1, 0), "22:00", "08:00"))
        assertFalse(NotificationCheckController.quiet(LocalTime.NOON, "22:00", "08:00"))
        assertTrue(NotificationCheckController.quiet(LocalTime.NOON, "invalid", "08:00"))
    }

    @Test fun onlyWhitelistedDestinationsAndMatchingVersionsAreAccepted() {
        assertEquals(NotificationDestination.HOME, NotificationDestination.fromKey("intent://arbitrary"))
        assertEquals(NotificationDestination.UNUSED_FILES, NotificationDestination.fromKey("unused_files"))
        assertTrue(NotificationCheckController.versionMatches(null, "1.0"))
        assertTrue(NotificationCheckController.versionMatches("1.0", "1.0"))
        assertFalse(NotificationCheckController.versionMatches("2.0", "1.0"))
    }
}
