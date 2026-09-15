package com.example.aicleanphonestorage

import android.app.ActivityManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import io.docview.push.service.CoreService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 保持生产宿主开关关闭，验证系统真实 FGS 启动请求能及时退出，不触发晋升超时。 */
@RunWith(AndroidJUnit4::class)
class CoreServiceDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    @Test fun disabledServiceStopsWithinForegroundStartupDeadline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(AboutActivity::class.java).use {
            for (action in listOf(null, "io.docview.push.START_KEEP_ALIVE_SERVICE")) {
                instrumentation.runOnMainSync {
                    context.startForegroundService(Intent(context, CoreService::class.java).setAction(action))
                }
                // 超过正常晋升期限后仍能执行断言，且本应用服务表没有遗留空壳实例。
                SystemClock.sleep(8_000)
                instrumentation.waitForIdleSync()
                @Suppress("DEPRECATION")
                val services = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
                assertFalse(services.any { service -> service.service.className == CoreService::class.java.name })
            }
        }
    }
}
