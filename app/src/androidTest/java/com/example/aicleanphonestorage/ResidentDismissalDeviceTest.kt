package com.example.aicleanphonestorage

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import io.docview.push.controller.TriggerCtrl
import io.docview.push.service.CoreService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只测 deleteIntent 原生回调与恢复行为，不测试/修改常驻通知视图。 */
@RunWith(AndroidJUnit4::class)
class ResidentDismissalDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    @Test fun nativeDeleteIntentRestoresSameIdOnlyWhileServiceRuns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = TriggerCtrl.getResidentNotificationId()
        runBlocking { (context.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            try {
                await { CoreService.isRunning && manager.activeNotifications.any { it.id == id && it.notification.deleteIntent != null } }
                val original = manager.activeNotifications.single { it.id == id }
                val token = requireNotNull(original.notification.deleteIntent)
                assertEquals(context.packageName, token.creatorPackage)
                if (Build.VERSION.SDK_INT >= 31) assertTrue(token.isImmutable)
                // 发送系统持有的真实 PendingIntent，进入 Manifest Receiver/goAsync/恢复逻辑。
                token.send()
                await { manager.activeNotifications.any { it.id == id && it.postTime > original.postTime } }
                val restored = manager.activeNotifications.filter { it.id == id }
                assertEquals("Restoration must reuse the resident ID", 1, restored.size)
                assertEquals(token, restored.single().notification.deleteIntent)
                assertTrue(restored.single().notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)

                // 模拟服务已被停止之后的迟到删除回调，不能重新拉起服务。
                scenario.moveToState(Lifecycle.State.CREATED)
                SystemClock.sleep(1_000)
                instrumentation.runOnMainSync { context.stopService(Intent(context, CoreService::class.java)) }
                await { !CoreService.isRunning }
                token.send()
                SystemClock.sleep(1_000)
                assertFalse("Delete callback must not restart a stopped service", CoreService.isRunning)
                assertFalse(manager.activeNotifications.any { it.id == id && it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 })
            } finally {
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.runOnMainSync { CoreService.startService(context) }
                await { CoreService.isRunning }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50)
        assertTrue("Notification lifecycle did not settle within 5 seconds", condition())
    }
}
