package com.example.aicleanphonestorage

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLaunchTelemetryDeviceTest {
    @Test fun clickAndEntrySurviveStartupWhitelistWithoutCopyingNotificationBody() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (source in listOf("resident", "local", "remote")) {
            val incoming = Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, "home")
                .putExtra(NotificationLaunchTelemetry.ORIGIN, source)
                .putExtra("landing_notification_content", "fixture body must not be forwarded")
            val entry = StartupNavigation.read(incoming)
            val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
            val sink = EventSink { event, values -> events += event to values }
            NotificationLaunchTelemetry.clicked(entry.notificationOrigin, sink)
            val normalized = StartupNavigation.startupIntent(context, entry)
            assertFalse(normalized.hasExtra("landing_notification_content"))
            val home = StartupNavigation.homeIntent(context, StartupNavigation.read(normalized))
            NotificationLaunchTelemetry.entered(home, sink)
            NotificationLaunchTelemetry.entered(home, sink)
            assertEquals(listOf("Notific_Click", "Notific_Enter"), events.map { it.first.wireName })
            assertEquals(if (source == "resident") 4 else if (source == "remote") 3 else 1, events.first().second["Notific_Type"])
            assertFalse(events.any { it.second.containsKey("text") || it.second.containsKey("title") })
        }
    }
    @Test fun localAndFcmIntentsAreRecognizedButOrdinaryLaunchIsNot() {
        val local = Intent().putExtra("from_notification", true)
        assertEquals("local", NotificationLaunchTelemetry.origin(local))
        local.putExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM, "firebase_push")
        assertEquals("remote", NotificationLaunchTelemetry.origin(local))
        assertEquals("resident", NotificationLaunchTelemetry.origin(Intent("fixture.resident.clean.shown")
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, "clean")))
        assertNull(NotificationLaunchTelemetry.origin(Intent()))
        NotificationLaunchTelemetry.clicked(null) { _, _ -> fail("Ordinary launch must not report notification clicks") }
    }
}
