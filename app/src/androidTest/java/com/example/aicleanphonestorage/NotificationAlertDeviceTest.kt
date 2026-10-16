package com.example.aicleanphonestorage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import io.docview.push.builder.GeneralNotificationData
import io.docview.push.controller.GeneralNotificationChannels
import io.docview.push.controller.TriggerCtrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 检查真实 Android 通道与生产构建器，不播放测试声音、不修改用户音量/勿扰或通道偏好。 */
@RunWith(AndroidJUnit4::class)
class NotificationAlertDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test fun newAndUnmodifiedLegacyDefaultsEnableSoundAndVibration() {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val fresh = GeneralNotificationChannels.alerting("Test", null)
        assertEquals(sound, fresh.sound)
        assertTrue(fresh.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, fresh.importance)
        val legacy = NotificationChannel(GeneralNotificationChannels.LEGACY, "Old", NotificationManager.IMPORTANCE_HIGH)
            .apply { enableVibration(false) }
        val migrated = GeneralNotificationChannels.alerting("Test", legacy)
        assertNotEquals(legacy.id, migrated.id)
        assertEquals(sound, migrated.sound)
        assertTrue(migrated.shouldVibrate())
        assertFalse("Migration does not mutate the legacy channel", legacy.shouldVibrate())
    }

    @Test fun knownUserMuteBlockingAndCustomSoundPreferencesArePreserved() {
        for (importance in listOf(NotificationManager.IMPORTANCE_NONE, NotificationManager.IMPORTANCE_LOW)) {
            val old = NotificationChannel(GeneralNotificationChannels.LEGACY, "Old", importance).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val migrated = GeneralNotificationChannels.alerting("Test", old)
            assertEquals(importance, migrated.importance)
            assertNull(migrated.sound)
            assertFalse(migrated.shouldVibrate())
            assertEquals(Notification.VISIBILITY_SECRET, migrated.lockscreenVisibility)
        }
        val custom = Uri.parse("content://test-notification-sound/custom")
        val pattern = longArrayOf(0, 80, 40, 80)
        val old = NotificationChannel(GeneralNotificationChannels.LEGACY, "Old", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(custom, null); vibrationPattern = pattern; enableVibration(true)
        }
        val migrated = GeneralNotificationChannels.alerting("Test", old)
        assertEquals(custom, migrated.sound)
        assertArrayEquals(pattern, migrated.vibrationPattern)
        assertTrue(migrated.shouldVibrate())
    }

    @Test fun firstTwoMessagesAllowAlertsAndRemainingRepeatsStaySilent() = runBlocking {
        (context.applicationContext as CleanApplication).notificationRuntime.awaitReady()
        val data = GeneralNotificationData(981739, "Alert policy test", "Test content")
        val firstMethod = TriggerCtrl::class.java.getDeclaredMethod("general", GeneralNotificationData::class.java)
            .apply { isAccessible = true }
        val first = firstMethod.invoke(TriggerCtrl, data) as Notification
        assertEquals(GeneralNotificationChannels.ALERTING, first.channelId)
        assertFalse(first.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(first.flags and Notification.FLAG_ONGOING_EVENT != 0)
        // 调用循环实际使用的构建入口，覆盖计数从 0 开始的边界，避免只测试手动指定的静音参数。
        val method = TriggerCtrl::class.java.getDeclaredMethod("buildRepeatNotification",
            GeneralNotificationData::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val second = method.invoke(TriggerCtrl, data, 0) as Notification
        assertEquals(GeneralNotificationChannels.ALERTING, second.channelId)
        assertFalse("Second notification must allow alerting the same ID again",
            second.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        for (completedRepeats in 1..7) {
            val followUp = method.invoke(TriggerCtrl, data, completedRepeats) as Notification
            assertEquals(TriggerCtrl.CHANNEL_ID_GENERAL_SILENT, followUp.channelId)
            assertFalse(followUp.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertEquals(0, followUp.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
            assertNull(followUp.sound)
            assertNull(followUp.vibrate)
        }
        val nextRoundSecond = method.invoke(TriggerCtrl, data, 0) as Notification
        assertEquals("A new round gets its own second alert", GeneralNotificationChannels.ALERTING, nextRoundSecond.channelId)
        val resident = TriggerCtrl.buildResidentNotification(context)
        assertEquals(TriggerCtrl.CHANNEL_ID_RESIDENT, resident.channelId)
        assertTrue(resident.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0, resident.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
    }

    @Test fun channelInitializationIsIdempotentAndMergedManifestSupportsVibration() = runBlocking {
        (context.applicationContext as CleanApplication).notificationRuntime.awaitReady()
        val before = requireNotNull(manager.getNotificationChannel(GeneralNotificationChannels.ALERTING))
        TriggerCtrl.initializeChannels(context)
        val after = requireNotNull(manager.getNotificationChannel(GeneralNotificationChannels.ALERTING))
        assertEquals(before.sound, after.sound)
        assertEquals(before.shouldVibrate(), after.shouldVibrate())
        assertEquals(before.importance, after.importance)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(android.Manifest.permission.VIBRATE))
        @Suppress("DEPRECATION")
        val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertEquals(GeneralNotificationChannels.ALERTING,
            metadata.getString("com.google.firebase.messaging.default_notification_channel_id"))
    }
}
