package com.example.aicleanphonestorage

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import io.docview.push.NotificationDestination
import io.docview.push.NotificationHost
import io.docview.push.alarm.AlarmWakeupReceiver
import io.docview.push.alarm.AlarmWakeupRegistration
import io.docview.push.alarm.parseAlarmWakeupConfig
import io.docview.push.builder.entryPointIntent
import io.docview.push.check.BlockReason
import io.docview.push.check.CheckCtrl
import io.docview.push.timing.TimingCtrl
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.corekit.core.controller.ChannelUserController
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟器专用真实 Alarm 冒烟：不修改生产间隔、SDK 归因、线上配置或扫描数据。
 * 长测试保持 instrumentation 进程存活，验证后台系统投递；不把它当成冷进程恢复测试。
 */
@RunWith(AndroidJUnit4::class)
class AlarmWakeupEmulatorTest {
    @get:Rule val noHotAds = NoHotStartAdsRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val runtime get() = (context.applicationContext as CleanApplication).notificationRuntime
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val store get() = context.getSharedPreferences("alarm_wakeup", Context.MODE_PRIVATE)

    @Test fun defaultsCancelNaturalAndSuppressForegroundPublication() = runBlocking {
        requireEmulator()
        runtime.awaitReady()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use {
                prepareAndArmDefault()
                runtime.setPaidUser(false)
                awaitNaturalCancellation()
            }
        } finally { restoreActualChannel() }
    }

    @Test fun realDefaultFifteenMinuteAlarmPublishesAndReschedules() = runBlocking {
        requireEmulator()
        // 避免每次 connectedAndroidTest 意外等待 20 分钟；只有明确指定的模拟器冒烟才运行。
        assumeTrue("Pass -e alarmEmulatorSoak true for the real 15-minute test",
            InstrumentationRegistry.getArguments().getString("alarmEmulatorSoak") == "true")
        runtime.awaitReady()
        val observer = AlarmPublicationObserver()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                val armed = prepareAndArmDefault()
                val previousAlarmTime = CheckCtrl.getInstance()
                    .getLastNotificationTime(CheckCtrl.NotificationType.ALARM_MANAGER)
                val armedElapsed = SystemClock.elapsedRealtime()
                val evidence = JSONObject().apply {
                    put("scope", "emulator_background_same_process")
                    put("defaultIntervalMinutes", 15)
                    put("alarmOverrideCached", !store.getString("config", "").isNullOrBlank())
                    put("naturalCancelled", true)
                    put("foregroundBlocked", true)
                    put("startedElapsed", armedElapsed)
                    put("due", armed.dueElapsedMillis)
                }
                saveEvidence(evidence)
                Log.i(TAG, "ARMED due=${armed.dueElapsedMillis} remaining_ms=${armed.dueElapsedMillis - armedElapsed}")

                scenario.moveToState(Lifecycle.State.CREATED)
                awaitCondition { !TimingCtrl.getInstance().isAppInForeground() }
                // 后台事件拥有独立来源。等它处理完再清掉共享槽位，不能把旧通知误判成 Alarm 成功。
                delay(1_500L)
                manager.cancel(GENERAL_NOTIFICATION_ID)
                assertFalse(manager.activeNotifications.any { it.id == GENERAL_NOTIFICATION_ID })
                assertTrue("Background alarm must pass the unchanged production admission rules",
                    CheckCtrl.getInstance().canTriggerNotificationWithReason(
                        CheckCtrl.NotificationType.ALARM_MANAGER, INTERVAL_MILLIS,
                    ).first)

                // 不模拟广播、不调时钟、不缩短周期；允许系统非精确合批迟到最多 5 分钟。
                val remaining = (armed.dueElapsedMillis + DELIVERY_TOLERANCE_MILLIS - SystemClock.elapsedRealtime())
                    .coerceAtLeast(1L)
                awaitCondition(remaining, pollMillis = 250L) {
                    observer.publication.get() != null && CheckCtrl.getInstance()
                        .getLastNotificationTime(CheckCtrl.NotificationType.ALARM_MANAGER) > previousAlarmTime
                }
                val publication = requireNotNull(observer.publication.get())
                assertTrue("System Alarm may be late but must not run early", publication.elapsed >= armed.dueElapsedMillis)
                assertEquals("alarmmanager", publication.source)
                assertEquals("true", publication.fromBackground)
                assertFalse(TimingCtrl.getInstance().isAppInForeground())
                val shown = requireNotNull(manager.activeNotifications.firstOrNull { it.id == GENERAL_NOTIFICATION_ID })
                assertTrue("Published notification must retain the alarmmanager click source",
                    isAlarmContentIntent(requireNotNull(shown.notification.contentIntent)))
                assertEquals(publication.title, shown.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
                assertEquals(publication.text, shown.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

                awaitCondition { registration()?.token?.let { it != armed.token } == true }
                val next = requireNotNull(registration())
                assertNotEquals(armed.token, next.token)
                assertNotNull(alarmPendingIntent())
                val now = SystemClock.elapsedRealtime()
                assertTrue("Next single alarm keeps the real 15-minute interval",
                    next.dueElapsedMillis in (publication.elapsed + INTERVAL_MILLIS)..(now + INTERVAL_MILLIS))
                evidence.apply {
                    put("deliveredElapsed", publication.elapsed)
                    put("deliveryDelayMillis", publication.elapsed - armed.dueElapsedMillis)
                    put("nextDue", next.dueElapsedMillis)
                    put("nextTokenDifferent", true)
                    put("alarmOverrideCachedAfterDelivery", !store.getString("config", "").isNullOrBlank())
                    put("trigger_source", publication.source)
                    put("from_background", publication.fromBackground)
                    put("title", publication.title)
                    put("text", publication.text)
                    put("passed", true)
                }
                saveEvidence(evidence)
                Log.i(TAG, "DELIVERED delay_ms=${publication.elapsed - armed.dueElapsedMillis} next_due=${next.dueElapsedMillis}")
                // 留给外部 adb 打开通知栏并保存实机截图；结束时恢复通知模块的实际渠道快照。
                delay(30_000L)
                runtime.setPaidUser(false)
                awaitNaturalCancellation()
            }
        } finally {
            observer.close()
            restoreActualChannel()
            manager.cancel(GENERAL_NOTIFICATION_ID)
        }
    }

    private suspend fun prepareAndArmDefault(): AlarmWakeupRegistration {
        val json = withContext(Dispatchers.IO) {
            context.assets.open("alarm_wakeup_config.json").bufferedReader().use { it.readText() }
        }
        val config = parseAlarmWakeupConfig(json)
        assertTrue(config.enabled)
        assertTrue(config.policyFor(confirmedPaid = true).enabled)
        assertFalse(config.policyFor(confirmedPaid = false).enabled)
        assertEquals(15, config.policyFor(confirmedPaid = true).intervalMinutes)
        // 已有 Pixel 模拟器可能保存有效线上覆盖；保持它完整，实际注册仍须满足本次要求的 15 分钟。
        assertTrue("The test emulator must grant notifications first", manager.areNotificationsEnabled())
        awaitCondition { TimingCtrl.getInstance().isAppInForeground() && store.getBoolean("has_launched", false) }
        runtime.setPaidUser(false)
        awaitNaturalCancellation()
        val started = SystemClock.elapsedRealtime()
        // 仅通知模块的会话快照；HomeCleaningSync 的 SDK 真实归因与持久化字段保持原值。
        runtime.setPaidUser(true)
        awaitCondition { registration() != null && alarmPendingIntent() != null }
        val registration = requireNotNull(registration())
        val now = SystemClock.elapsedRealtime()
        assertTrue("Default scheduling must be exactly 15 minutes from the actual registration",
            registration.dueElapsedMillis in (started + INTERVAL_MILLIS)..(now + INTERVAL_MILLIS))
        val check = CheckCtrl.getInstance().canTriggerNotificationWithReason(
            CheckCtrl.NotificationType.ALARM_MANAGER, INTERVAL_MILLIS,
        )
        assertFalse(check.first)
        assertEquals(BlockReason.APP_IN_FOREGROUND, check.second)
        return registration
    }

    private suspend fun awaitNaturalCancellation() = awaitCondition {
        registration() == null && alarmPendingIntent() == null
    }

    private fun registration(): AlarmWakeupRegistration? = store.getString("token", null)?.let {
        AlarmWakeupRegistration(it, store.getInt("boot", -1), store.getLong("due", -1L))
    }

    private fun alarmPendingIntent(): PendingIntent? = PendingIntent.getBroadcast(context, 4103,
        Intent(context, AlarmWakeupReceiver::class.java).setAction("io.docview.push.ALARM_WAKEUP"),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    private fun isAlarmContentIntent(actual: PendingIntent): Boolean = NotificationDestination.entries.any { destination ->
        val intent = entryPointIntent(context).apply {
            action = "${context.packageName}.push.$GENERAL_NOTIFICATION_ID.${destination.contentType}.alarmmanager"
        }
        actual == PendingIntent.getActivity(context, GENERAL_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun awaitCondition(timeoutMillis: Long = 10_000L, pollMillis: Long = 25L, condition: () -> Boolean) {
        withTimeout(timeoutMillis) { while (!condition()) delay(pollMillis) }
    }

    private suspend fun saveEvidence(evidence: JSONObject) = withContext(Dispatchers.IO) {
        val directory = File(context.getExternalFilesDir(null), "alarm-emulator").apply { check(mkdirs() || isDirectory) }
        File(directory, "evidence.json").writeText(evidence.toString(2))
    }

    private suspend fun restoreActualChannel() {
        val paid = withContext(Dispatchers.IO) {
            ChannelUserController.getCurrentChannel() == ChannelUserController.UserChannelType.PAID
        }
        runtime.setPaidUser(paid)
        if (!paid) awaitNaturalCancellation()
    }

    private fun requireEmulator() {
        assumeTrue("Real Alarm tests are restricted to an Android emulator",
            Build.HARDWARE in setOf("goldfish", "ranchu") || Build.FINGERPRINT.contains("emulator", ignoreCase = true))
    }

    private data class AlarmPublication(val elapsed: Long, val source: String, val fromBackground: String,
        val title: String, val text: String)

    /** 只观察宿主现有事件边界，转发原事件；不添加生产测试入口，也不保存整份通知 extras。 */
    private class AlarmPublicationObserver : AutoCloseable {
        val publication = AtomicReference<AlarmPublication?>()
        private val hostField = Class.forName("io.docview.push.host.PushEnvironment")
            .getDeclaredField("host").apply { isAccessible = true }
        private val original = hostField.get(null) as NotificationHost
        init {
            hostField.set(null, object : NotificationHost by original {
                override fun onEvent(name: String, properties: Map<String, Any?>) {
                    if (name == "Notific_Show" && properties["trigger_source"] == "alarmmanager") {
                        publication.compareAndSet(null, AlarmPublication(SystemClock.elapsedRealtime(),
                            properties["trigger_source"].toString(), properties["from_background"].toString(),
                            properties["title"].toString(), properties["text"].toString()))
                    }
                    original.onEvent(name, properties)
                }
            })
        }
        override fun close() { hostField.set(null, original) }
    }

    companion object {
        private const val TAG = "AlarmEmulatorTest"
        private const val GENERAL_NOTIFICATION_ID = 10000
        private const val INTERVAL_MILLIS = 15 * 60_000L
        private const val DELIVERY_TOLERANCE_MILLIS = 5 * 60_000L
    }
}
