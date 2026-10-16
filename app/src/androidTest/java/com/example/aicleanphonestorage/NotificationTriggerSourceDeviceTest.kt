package com.example.aicleanphonestorage

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.push.NotificationLaunchTelemetry
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import io.docview.push.NotificationDestination
import io.docview.push.NotificationHost
import io.docview.push.analytics.NotificationContent
import io.docview.push.analytics.NotificationTriggerSource
import io.docview.push.builder.GeneralModelManager
import io.docview.push.builder.LANDING_NOTIFICATION_FROM
import io.docview.push.builder.entryPointIntent
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import io.docview.push.config.ContentController
import io.docview.push.controller.TriggerCtrl
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.corekit.core.controller.ChannelUserController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 测试来源贯穿上报与白名单启动快照；不点击通知，不进入真实启动广告或业务扫描。 */
@RunWith(AndroidJUnit4::class)
class NotificationTriggerSourceDeviceTest {
    @get:Rule val noHotAds = NoHotStartAdsRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val runtime get() = (context.applicationContext as CleanApplication).notificationRuntime

    private val ordinarySources = listOf(
        CheckCtrl.NotificationType.UNLOCK to "unlock",
        CheckCtrl.NotificationType.BACKGROUND to "background",
        CheckCtrl.NotificationType.KEEPALIVE to "keepalive",
        CheckCtrl.NotificationType.ALARM_MANAGER to "alarmmanager",
        CheckCtrl.NotificationType.FCM to "fcm",
    )

    @Test fun actualPublicationsAndStartupSnapshotsKeepAllSixSourcesAndLegacyTypes() = runBlocking {
        assumeTrue("Real notification publication is restricted to an emulator",
            Build.HARDWARE in setOf("goldfish", "ranchu") || Build.FINGERPRINT.contains("emulator", true))
        runtime.awaitReady()
        assumeTrue("Grant notifications through the Android system before this test",
            NotificationManagerCompat.from(context).areNotificationsEnabled())
        val manager = context.getSystemService(NotificationManager::class.java)
        val previous = manager.activeNotifications.firstOrNull { it.id == GENERAL_ID }?.notification
        val evidence = JSONArray()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use {
                // 仅修改通知模块会话归因，结束后恢复 SDK 实际渠道；不改 SDK 持久化归因或远程配置。
                runtime.setPaidUser(true)
                withTimeout(5_000) { while (!ContentController.isInitialized()) delay(25) }
                PublicationObserver().use { observer ->
                    for ((type, source) in ordinarySources) {
                        // 公开发送核心跳过 TimingCtrl 的频次准入，仍走真实构建、权限复核和系统 notify。
                        TriggerCtrl.triggerGeneralNotification(type)
                        val show = observer.awaitSource(source)
                        // notify 返回时系统服务可能仍在处理队列，等待对应来源真正进入系统通知列表。
                        val posted = withTimeout(5_000) {
                            var current = manager.activeNotifications.firstOrNull { it.id == GENERAL_ID }
                            while (!pendingIntentFor(source, current?.notification?.contentIntent)) {
                                delay(25)
                                current = manager.activeNotifications.firstOrNull { it.id == GENERAL_ID }
                            }
                            requireNotNull(current)
                        }
                        assertTrue("Published $source card must use its own PendingIntent",
                            pendingIntentFor(source, posted.notification.contentIntent))
                        val expectedType = if (source == "fcm") 3 else 1
                        evidence.put(checkThreeEvents(show, source, expectedType))
                    }
                    TriggerCtrl.triggerResidentNotification()
                    val resident = observer.awaitSource("resident")
                    evidence.put(checkThreeEvents(resident, "resident", 4))
                    // 同 ID 也被 FGS 使用，系统可暂缓其首次可见；不把 notify 返回误当作队列已发布。
                    val postedResident = withTimeout(15_000) {
                        var current = manager.activeNotifications.firstOrNull { it.id == TriggerCtrl.getResidentNotificationId() }
                        while (current == null) {
                            delay(25)
                            current = manager.activeNotifications.firstOrNull { it.id == TriggerCtrl.getResidentNotificationId() }
                        }
                        current
                    }
                    assertNotNull(postedResident)
                }
            }
        } finally {
            instrumentation.runOnMainSync { TriggerCtrl.stopRepeatNotification() }
            val paid = withContext(Dispatchers.IO) {
                ChannelUserController.getCurrentChannel() == ChannelUserController.UserChannelType.PAID
            }
            runtime.setPaidUser(paid)
            if (previous == null) manager.cancel(GENERAL_ID) else manager.notify(GENERAL_ID, previous)
        }
        val folder = File(context.getExternalFilesDir(null), "notification-trigger-source").apply { mkdirs() }
        File(folder, "evidence.json").writeText(evidence.toString(2))
    }

    @Test fun realGeneralBuildersSeparatePendingIntentsBySourceAndResidentShortcutsCarrySource() = runBlocking {
        runtime.awaitReady()
        val content = Content("source_fixture", "Review storage", "Choose what to keep", "Open", Content.TYPE_HOME, Content.TYPE_HOME)
        val pending = ordinarySources.map { (type, source) ->
            assertEquals(source, type.triggerSource?.value)
            val model = GeneralModelManager().build(context, content, type)
            val actual = requireNotNull(model.contentIntent)
            assertTrue(pendingIntentFor(source, actual, Content.TYPE_HOME))
            assertEquals("Rebuilding one source should reuse only that source's token", actual,
                GeneralModelManager().build(context, content.copy(desc = "Updated body"), type).contentIntent)
            actual
        }
        assertEquals("An unpublished build from another source must not overwrite an existing card", pending.size, pending.toSet().size)
        val residentContent = NotificationContent("Storage", "Clean / Apps / Photos")
        for ((entry, destination) in listOf("clean" to NotificationDestination.CLEAN,
            "app" to NotificationDestination.APP_MANAGER, "photos" to NotificationDestination.SCREENSHOTS,
            "accelerate" to NotificationDestination.APP_MANAGER)) {
            val shortcut = NotificationNavigation.residentIntent(context, destination, entry, "none", null, residentContent)
            assertEquals("resident", shortcut.getStringExtra(NotificationTriggerSource.EXTRA))
            assertEquals("resident", StartupNavigation.read(shortcut, true).notification?.triggerSource)
            assertEquals(destination, NotificationNavigation.read(shortcut))
        }
    }

    @Test fun oldFcmResidentAndAlarmIntentsAreAttributedWithoutGuessingAmbiguousLocalSources() {
        val cases = listOf(
            Intent().putExtra("from_notification", true).putExtra(LANDING_NOTIFICATION_FROM, "firebase_push") to ("fcm" to 3),
            Intent().putExtra("google.message_id", "fixture_google") to ("fcm" to 3),
            Intent().putExtra("gcm.message_id", "fixture_gcm") to ("fcm" to 3),
            // 旧 top_notification 原先落在 local 的 Notific_Type=1，仅补齐触发源，不重写历史分类。
            Intent().putExtra("from_notification", true).putExtra(LANDING_NOTIFICATION_FROM, "top_notification") to ("resident" to 1),
            Intent("fixture.resident.clean.none.unknown").putExtra(NotificationNavigation.EXTRA_DESTINATION, "clean") to ("resident" to 4),
            Intent().putExtra("from_notification", true).putExtra(LANDING_NOTIFICATION_FROM, "alarmmanager") to ("alarmmanager" to 1),
        )
        for ((intent, expected) in cases) {
            val snapshot = requireNotNull(NotificationLaunchTelemetry.read(intent, false))
            assertEquals(expected.first, snapshot.triggerSource)
            assertEquals(expected.second, snapshot.properties()["Notific_Type"])
        }
        val ambiguous = Intent().putExtra("from_notification", true).putExtra(LANDING_NOTIFICATION_FROM, "local_push")
        assertFalse(requireNotNull(NotificationLaunchTelemetry.read(ambiguous)).properties().containsKey("trigger_source"))
    }

    private fun checkThreeEvents(show: Map<String, Any?>, source: String, type: Int): JSONObject {
        assertEquals(source, show["trigger_source"])
        assertEquals(type, show["Notific_Type"])
        val output = JSONObject().put("source", source).put("show", JSONObject(show))
        for (background in listOf(true, false)) {
            val incoming = Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, "home")
                .putExtra(NotificationLaunchTelemetry.ORIGIN, if (source == "resident") "resident" else if (source == "fcm") "remote" else "local")
                .putExtra(NotificationTriggerSource.EXTRA, source)
                .putExtra("landing_notification_title", show["title"] as String)
                .putExtra("landing_notification_content", show["text"] as String)
            val entry = StartupNavigation.read(incoming, background)
            val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
            val sink = EventSink { event, values -> events += event to values }
            val normalized = StartupNavigation.startupIntent(context, entry)
            // 启动页先使用点击快照配对上报，首页收到的路由不再携带已消费的埋点快照。
            val restored = StartupNavigation.read(normalized, !background)
            NotificationLaunchTelemetry.entered(restored.notification, sink)
            assertEquals(2, events.size)
            val home = StartupNavigation.homeIntent(context, restored.copy(notification = null))
            NotificationLaunchTelemetry.entered(home, sink)
            NotificationLaunchTelemetry.entered(home, sink)
            assertEquals(listOf(MetricEvent.NOTIFICATION_CLICK, MetricEvent.NOTIFICATION_ENTER), events.map { it.first })
            assertEquals(events[0].second, events[1].second)
            val click = events[0].second
            assertEquals(source, click["trigger_source"])
            assertEquals(type, click["Notific_Type"])
            assertEquals(background.toString(), click["from_background"])
            assertEquals(show["title"], click["title"])
            assertEquals(show["text"], click["text"])
            assertFalse(home.hasExtra(NotificationTriggerSource.EXTRA))
            output.put("click_background_$background", JSONObject(click)).put("enter_background_$background", JSONObject(events[1].second))
        }
        return output
    }

    private fun pendingIntentFor(source: String, actual: PendingIntent?, destination: Int? = null): Boolean =
        (destination?.let { listOf(it) } ?: NotificationDestination.entries.map { it.contentType }).any { action ->
            // 实际发布轮播的业务目标可变化；直接构建测试使用固定 HOME。
            val identity = entryPointIntent(context).apply { this.action = "${context.packageName}.push.$GENERAL_ID.$action.$source" }
            actual != null && actual == PendingIntent.getActivity(context, GENERAL_ID, identity,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        }

    /** 只替换测试期间的事件出口；真实 notify 由生产发送核心执行，不增加生产测试开关。 */
    private class PublicationObserver : AutoCloseable {
        private val events = Channel<Map<String, Any?>>(16)
        private val hostField = Class.forName("io.docview.push.host.PushEnvironment")
            .getDeclaredField("host").apply { isAccessible = true }
        private val original = hostField.get(null) as NotificationHost
        init {
            hostField.set(null, object : NotificationHost by original {
                override fun onEvent(name: String, properties: Map<String, Any?>) {
                    if (name == "Notific_Show") events.trySend(properties.toMap())
                    else original.onEvent(name, properties)
                }
            })
        }
        suspend fun awaitSource(source: String): Map<String, Any?> = withTimeout(5_000) {
            var next = events.receive()
            while (next["trigger_source"] != source) next = events.receive()
            next
        }
        override fun close() { hostField.set(null, original); events.close() }
    }

    companion object { private const val GENERAL_ID = 10000 }
}
