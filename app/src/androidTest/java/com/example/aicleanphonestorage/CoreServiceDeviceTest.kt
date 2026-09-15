package com.example.aicleanphonestorage

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import io.docview.push.service.CoreService
import io.docview.push.timing.TimingCtrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 读取系统实际注册表验证双层生命周期，不以本地布尔标记代替接收器真实存在。 */
@RunWith(AndroidJUnit4::class)
class CoreServiceDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    @Test fun twoOwnersRegisterIndependentlyAndRepeatedServiceStartDoesNotAddReceivers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runBlocking { (context.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        ActivityScenario.launch(AboutActivity::class.java).use {
            await { CoreService.isRunning }
            // 服务异步等待 Runtime 准备好再注册；初次稳定后记下包含 SDK 接收器的基准。
            SystemClock.sleep(1_000)
            val baseline = screenReceiverCount()
            assertTrue("Application and Service screen listeners must both exist", baseline >= 2)
            try {
                instrumentation.runOnMainSync {
                    repeat(3) {
                        context.startForegroundService(Intent(context, CoreService::class.java)
                            .setAction("io.docview.push.START_KEEP_ALIVE_SERVICE"))
                    }
                }
                SystemClock.sleep(500)
                assertEquals("Repeated start must not register again", baseline, screenReceiverCount())

                instrumentation.runOnMainSync { TimingCtrl.getInstance().release() }
                assertEquals("Application release must retain Service listener", baseline - 1, screenReceiverCount())
                assertTrue(CoreService.isRunning)

                instrumentation.runOnMainSync { TimingCtrl.getInstance().initialize(context) }
                assertEquals(baseline, screenReceiverCount())

                instrumentation.runOnMainSync { context.stopService(Intent(context, CoreService::class.java)) }
                await { !CoreService.isRunning }
                assertEquals("Service destruction must retain Application listener", baseline - 1, screenReceiverCount())
            } finally {
                instrumentation.runOnMainSync {
                    TimingCtrl.getInstance().initialize(context)
                    CoreService.startService(context)
                }
                await { CoreService.isRunning }
            }
        }
    }

    private fun screenReceiverCount(): Int {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity broadcasts $pkg")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }.substringBefore("Receiver Resolver Table:")
        return dump.split("* ReceiverList{").count { block ->
            block.contains(pkg) && block.contains("Action: \"android.intent.action.SCREEN_OFF\"") &&
                block.contains("Action: \"android.intent.action.SCREEN_ON\"") &&
                block.contains("Action: \"android.intent.action.USER_PRESENT\"")
        }
    }

    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50)
        assertTrue("Service lifecycle did not settle within 5 seconds", condition())
    }
}
