package com.example.aicleanphonestorage.feature.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationClickContextTest {
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
}
