package com.example.aicleanphonestorage

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import io.docview.push.analytics.NotificationContentIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLaunchTelemetryDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    @Test fun actualActivityVisibilityIsSampledBeforeNotificationNavigation() {
        androidx.test.core.app.ActivityScenario.launch(com.example.aicleanphonestorage.feature.settings.AboutActivity::class.java).use { scenario ->
            val input = Intent().putExtra("from_notification", true)
            assertEquals(false, NotificationLaunchTelemetry.read(input)?.fromBackground)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertEquals(true, NotificationLaunchTelemetry.read(input)?.fromBackground)
        }
    }

    @Test fun clickAndEntryRetainSameContentAndOriginalBackgroundThroughStartup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (source in listOf("resident", "local", "remote")) for (background in listOf(true, false)) {
            val incoming = Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, "home")
                .putExtra(NotificationLaunchTelemetry.ORIGIN, source)
                .putExtra("landing_notification_title", "$source title")
                .putExtra("landing_notification_content", "$source body")
                .putExtra("external_uri", "intent://untrusted")
            val entry = StartupNavigation.read(incoming, fromBackground = background)
            val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
            val sink = EventSink { event, values -> events += event to values }
            NotificationLaunchTelemetry.clicked(entry.notification, sink)
            val normalized = StartupNavigation.startupIntent(context, entry)
            assertFalse(normalized.hasExtra("landing_notification_content"))
            assertFalse(normalized.hasExtra("external_uri"))
            assertEquals("$source body", normalized.getStringExtra(NotificationContentIntent.TEXT))
            // 启动广告/权限结束后应用状态可能反转，Enter 仍必须沿用点击时的快照。
            val home = StartupNavigation.homeIntent(context, StartupNavigation.read(normalized, fromBackground = !background))
            NotificationLaunchTelemetry.entered(home, sink)
            NotificationLaunchTelemetry.entered(home, sink)
            assertEquals(listOf("Notific_Click", "Notific_Enter"), events.map { it.first.wireName })
            assertEquals(events.first().second, events.last().second)
            assertEquals(background.toString(), events.first().second["from_background"])
            assertEquals("$source title", events.first().second["title"])
            assertEquals("$source body", events.first().second["text"])
            assertEquals(if (source == "resident") 4 else if (source == "remote") 3 else 1, events.first().second["Notific_Type"])
            assertFalse(home.hasExtra(NotificationContentIntent.TEXT))
            assertFalse(home.hasExtra(NotificationLaunchTelemetry.ORIGIN))
        }
    }

    @Test fun localAndFcmIntentsAreRecognizedButOrdinaryLaunchIsNot() {
        val local = Intent().putExtra("from_notification", true)
        assertEquals("local", NotificationLaunchTelemetry.origin(local))
        local.putExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM, "firebase_push")
        assertEquals("remote", NotificationLaunchTelemetry.origin(local))
        assertEquals("resident", NotificationLaunchTelemetry.origin(Intent("fixture.resident.clean.shown")
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, "clean")))
        assertNull(NotificationLaunchTelemetry.read(Intent()))
        NotificationLaunchTelemetry.clicked(null) { _, _ -> fail("Ordinary launch must not report notification clicks") }
    }

    @Test fun largeNotificationExtrasAreBoundedAndResidentContentUsesActualResourceText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = Intent().putExtra("from_notification", true)
            .putExtra("landing_notification_title", "t".repeat(1000))
            .putExtra("landing_notification_content", "x".repeat(10000))
        val home = StartupNavigation.homeIntent(context, StartupNavigation.read(input, true))
        assertEquals(120, home.getStringExtra(NotificationContentIntent.TITLE)!!.length)
        assertEquals(500, home.getStringExtra(NotificationContentIntent.TEXT)!!.length)
        val content = CleanNotificationHost(context).residentContent()
        assertTrue(content.title.isNotBlank())
        assertTrue(content.text.isNotBlank())
    }
}
