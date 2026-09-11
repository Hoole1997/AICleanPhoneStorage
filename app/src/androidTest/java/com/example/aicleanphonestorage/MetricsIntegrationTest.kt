package com.example.aicleanphonestorage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.report.ReportDataManager
import net.corekit.metrics.BuildConfig as MetricsConfig
import net.corekit.metrics.adjust.AdjustTracker
import net.corekit.metrics.data.ThinkingReporter
import net.corekit.metrics.provider.MetricsModuleProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 校验真实模块配置与初始化幂等，不构造虚假的业务/收益事件，也不以初始化代替后台收数验证。 */
@RunWith(AndroidJUnit4::class)
class MetricsIntegrationTest {
    @Test fun flavorConfigurationAndReporterRegistrationRemainConsistent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.FLAVOR, MetricsConfig.FLAVOR)
        assertEquals(BuildConfig.ADJUST_APP_TOKEN, MetricsConfig.ADJUST_APP_TOKEN)
        assertEquals(BuildConfig.THINKING_DATA_APP_ID, MetricsConfig.THINKING_DATA_APP_ID)
        assertEquals(BuildConfig.THINKING_DATA_SERVER_URL, MetricsConfig.THINKING_DATA_SERVER_URL)
        assertEquals(BuildConfig.DEFAULT_USER_CHANNEL, MetricsConfig.DEFAULT_USER_CHANNEL)
        assertNotNull(context.packageManager.resolveContentProvider("${context.packageName}.metrics.provider", 0))
        coroutineScope { repeat(3) { launch { MetricsModuleProvider.initialize(context) } } }
        val firebase = FirebaseApp.getApps(context).isNotEmpty()
        val adjust = MetricsConfig.ADJUST_APP_TOKEN.isNotBlank()
        val thinking = MetricsConfig.THINKING_DATA_APP_ID.isNotBlank() && MetricsConfig.THINKING_DATA_SERVER_URL.isNotBlank()
        assertEquals(adjust, AdjustTracker.checkInitialized())
        assertEquals(thinking, ThinkingReporter.checkInitialized())
        assertEquals(firebase || thinking, ReportDataManager.isInitialized())
        assertEquals((if (firebase) 1 else 0) + (if (adjust) 1 else 0), RevenueAdManager.getReporterCount())
    }
}
