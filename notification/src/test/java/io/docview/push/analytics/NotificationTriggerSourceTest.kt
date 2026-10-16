package io.docview.push.analytics

import io.docview.push.check.CheckCtrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTriggerSourceTest {
    @Test fun canonicalValuesAreUniqueAndRoundTripThroughTheSharedWhitelist() {
        val values = listOf("unlock", "background", "keepalive", "alarmmanager", "fcm", "resident")
        assertEquals(values, NotificationTriggerSource.entries.map { it.value })
        values.forEach { value ->
            val source = requireNotNull(NotificationTriggerSource.fromValue(value))
            assertEquals(mapOf("trigger_source" to value), source.properties())
        }
    }

    @Test fun unknownAndLegacyRouteValuesAreNotGuessedAsFineGrainedSources() {
        listOf(null, "", "local_push", "firebase_push", "top_notification", "AlarmManager", "x".repeat(5_000))
            .forEach { assertNull(NotificationTriggerSource.fromValue(it)) }
    }

    @Test fun notificationTypesKeepLegacyRoutesAndExposeTheirIndependentSource() {
        val expected = mapOf(
            CheckCtrl.NotificationType.UNLOCK to ("local_push" to NotificationTriggerSource.UNLOCK),
            CheckCtrl.NotificationType.BACKGROUND to ("local_push" to NotificationTriggerSource.BACKGROUND),
            CheckCtrl.NotificationType.KEEPALIVE to ("local_push" to NotificationTriggerSource.KEEPALIVE),
            CheckCtrl.NotificationType.ALARM_MANAGER to ("alarmmanager" to NotificationTriggerSource.ALARM_MANAGER),
            CheckCtrl.NotificationType.FCM to ("firebase_push" to NotificationTriggerSource.FCM),
            CheckCtrl.NotificationType.RESIDENT to ("top_notification" to NotificationTriggerSource.RESIDENT),
        )
        expected.forEach { (type, protocol) ->
            assertEquals(protocol.first, type.string)
            assertEquals(protocol.second, type.triggerSource)
        }
        assertEquals("earthquake", CheckCtrl.NotificationType.EARTHQUAKE.string)
        assertNull(CheckCtrl.NotificationType.EARTHQUAKE.triggerSource)
    }
}
