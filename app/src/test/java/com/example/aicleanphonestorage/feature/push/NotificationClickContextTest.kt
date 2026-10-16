package com.example.aicleanphonestorage.feature.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import io.docview.push.analytics.NotificationTriggerSource

class NotificationClickContextTest {
    @Test
    fun everyTriggerRetainsItsSourceAndExistingNotificationType() {
        for (source in NotificationTriggerSource.entries) {
            val origin = when (source) {
                NotificationTriggerSource.RESIDENT -> "resident"
                NotificationTriggerSource.FCM -> "remote"
                else -> "local"
            }
            for (background in listOf(true, false)) {
                val snapshot = requireNotNull(NotificationClickContext.create(
                    origin, "Title", "Body", background, source.value,
                ))
                assertEquals(source.value, snapshot.triggerSource)
                assertEquals(source.value, snapshot.properties()["trigger_source"])
                assertEquals(when (origin) { "resident" -> 4; "remote" -> 3; else -> 1 },
                    snapshot.properties()["Notific_Type"])
                assertEquals(background.toString(), snapshot.properties()["from_background"])
            }
        }
    }

    @Test
    fun oldResidentAndRemoteSnapshotsHaveUnambiguousSources() {
        assertEquals("resident", NotificationClickContext.create("resident", "", "", false)?.triggerSource)
        assertEquals("fcm", NotificationClickContext.create("remote", "", "", true)?.triggerSource)
        assertNull(NotificationClickContext.create("local", "", "", true)?.triggerSource)
    }

    @Test
    fun alarmTriggerIsDistinctWhileRetainingLocalNotificationType() {
        val snapshot = requireNotNull(NotificationClickContext.create(
            "local", "Title", "Body", true, triggerSource = "alarmmanager",
        ))

        assertEquals("alarmmanager", snapshot.triggerSource)
        assertEquals("alarmmanager", snapshot.properties()["trigger_source"])
        assertEquals(1, snapshot.properties()["Notific_Type"])
        assertEquals("true", snapshot.properties()["from_background"])
    }

    @Test
    fun legacyAndUntrustedSourcesDoNotAddAPropertyOrRetainExternalData() {
        for (trigger in listOf(null, "local_push", "firebase_push", "AlarmManager", "x".repeat(10_000))) {
            val snapshot = requireNotNull(NotificationClickContext.create(
                "local", "Title", "Body", false, triggerSource = trigger,
            ))
            assertNull(snapshot.triggerSource)
            assertFalse(snapshot.properties().containsKey("trigger_source"))
        }
    }

    @Test
    fun manuallyCopiedUntrustedSourceCannotEnterTelemetry() {
        val snapshot = requireNotNull(NotificationClickContext.create("local", "", "", false))
            .copy(triggerSource = "external".repeat(1000))
        assertFalse(snapshot.properties().containsKey("trigger_source"))
    }
}
