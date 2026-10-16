package com.example.aicleanphonestorage

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import com.example.aicleanphonestorage.feature.startup.StartupEntry
import com.example.aicleanphonestorage.feature.startup.StartupViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
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
            val normalized = StartupNavigation.startupIntent(context, entry)
            assertFalse(normalized.hasExtra("landing_notification_content"))
            assertFalse(normalized.hasExtra("external_uri"))
            assertEquals("$source body", normalized.getStringExtra(NotificationContentIntent.TEXT))
            // 进入启动页就配对上报；广告/权限尚未结束，也不需要等待首页。
            val consumed = enterStartup(StartupNavigation.read(normalized, fromBackground = !background), sink)
            assertEquals(listOf("Notific_Click", "Notific_Enter"), events.map { it.first.wireName })
            val home = StartupNavigation.homeIntent(context, consumed)
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
        NotificationLaunchTelemetry.entered(null as NotificationClickContext?) { _, _ -> fail("Ordinary launch must not report notification clicks") }
    }

    @Test fun alarmTriggerSurvivesStartupAndIsConsumedWithTheNotificationSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val incoming = Intent().putExtra("from_notification", true)
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, "clean")
            .putExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM, "alarmmanager")
            .putExtra("landing_notification_title", "Alarm title")
            .putExtra("landing_notification_content", "Alarm body")
        val entry = StartupNavigation.read(incoming, fromBackground = true)
        assertEquals("local", entry.notification?.origin)
        assertEquals("alarmmanager", entry.notification?.triggerSource)
        val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val sink = EventSink { event, values -> events += event to values }

        val normalized = StartupNavigation.startupIntent(context, entry)
        assertEquals("alarmmanager", normalized.getStringExtra(NotificationLaunchTelemetry.TRIGGER_SOURCE))
        assertFalse(normalized.hasExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM))
        val consumed = enterStartup(StartupNavigation.read(normalized, fromBackground = false), sink)
        assertEquals(2, events.size)
        val home = StartupNavigation.homeIntent(context, consumed)
        NotificationLaunchTelemetry.entered(home, sink)
        NotificationLaunchTelemetry.entered(home, sink)

        assertEquals(listOf(MetricEvent.NOTIFICATION_CLICK, MetricEvent.NOTIFICATION_ENTER), events.map { it.first })
        assertEquals(events.first().second, events.last().second)
        assertEquals("alarmmanager", events.last().second["trigger_source"])
        assertEquals(1, events.last().second["Notific_Type"])
        assertEquals("true", events.last().second["from_background"])
        assertFalse(home.hasExtra(NotificationLaunchTelemetry.TRIGGER_SOURCE))
        assertFalse(home.hasExtra(NotificationLaunchTelemetry.ORIGIN))
    }

    @Test fun directHomeConsumesRawNotificationOnceAndNewClickCanReportAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val sink = EventSink { event, values -> events += event to values }
        val raw = Intent().putExtra("from_notification", true)
            .putExtra(StartupNavigation.COMPLETED, true)
            .putExtra(NotificationLaunchTelemetry.FROM_BACKGROUND, false)
            .putExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM, "alarmmanager")
            .putExtra("landing_notification_title", "Direct title")
            .putExtra("landing_notification_content", "Direct body")
        assertFalse(StartupNavigation.needsStartup(raw))
        val nextClick = Intent(raw)
        NotificationLaunchTelemetry.entered(raw, sink)
        NotificationLaunchTelemetry.entered(raw, sink)
        NotificationLaunchTelemetry.entered(Intent(raw), sink) // 系统重建保留已消费标记。
        assertEquals(listOf(MetricEvent.NOTIFICATION_CLICK, MetricEvent.NOTIFICATION_ENTER), events.map { it.first })
        assertEquals(events[0].second, events[1].second)
        assertEquals("false", events[1].second["from_background"])
        assertEquals("alarmmanager", events[1].second["trigger_source"])
        assertEquals("Direct title", events[1].second["title"])
        // 保留业务目标；消费埋点不能阻断后续首页路由。
        assertNotNull(NotificationNavigation.read(raw))
        assertNull(NotificationLaunchTelemetry.read(raw))
        NotificationLaunchTelemetry.entered(nextClick, sink)
        assertEquals(listOf(MetricEvent.NOTIFICATION_CLICK, MetricEvent.NOTIFICATION_ENTER,
            MetricEvent.NOTIFICATION_CLICK, MetricEvent.NOTIFICATION_ENTER), events.map { it.first })
        NotificationLaunchTelemetry.entered(Intent(context, com.example.aicleanphonestorage.app.MainActivity::class.java), sink)
        assertEquals(4, events.size)
    }

    /** 复用生产启动状态消费逻辑；只注入本地事件出口，不打开广告或发送虚构埋点。 */
    private fun enterStartup(entry: StartupEntry, sink: EventSink): StartupEntry {
        lateinit var outgoing: StartupEntry
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val saved = SavedStateHandle()
            val store = ViewModelStore()
            try {
                val model = StartupViewModel(saved, prepare = {}).also { store.put("original", it) }
                model.accept(entry)
                NotificationLaunchTelemetry.entered(model.consumeNotificationEntry(), sink)
                NotificationLaunchTelemetry.entered(model.consumeNotificationEntry(), sink)
                val restored = StartupViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
                    prepare = {}).also { store.put("restored", it) }
                NotificationLaunchTelemetry.entered(restored.consumeNotificationEntry(), sink)
                outgoing = restored.entry()
                assertEquals(entry.destination, outgoing.destination)
                assertNull(outgoing.notification)
            } finally { store.clear() }
        }
        return outgoing
    }

    @Test fun unknownTriggerIsDroppedAndWritingLegacyContextClearsPriorTrigger() {
        val input = Intent().putExtra("from_notification", true)
            .putExtra(io.docview.push.builder.LANDING_NOTIFICATION_FROM, "untrusted".repeat(1000))
        val snapshot = requireNotNull(NotificationLaunchTelemetry.read(input, fromBackground = false))
        assertNull(snapshot.triggerSource)
        assertFalse(snapshot.properties().containsKey("trigger_source"))
        val normalized = Intent().putExtra(NotificationLaunchTelemetry.TRIGGER_SOURCE, "alarmmanager")
        NotificationLaunchTelemetry.write(normalized, snapshot)
        assertFalse(normalized.hasExtra(NotificationLaunchTelemetry.TRIGGER_SOURCE))
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
