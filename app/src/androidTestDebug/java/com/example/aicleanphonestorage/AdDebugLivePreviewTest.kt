package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.android.common.bill.ads.bidding.AdSourceController
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor
import com.example.aicleanphonestorage.app.ad.AdInitializationState
import com.example.aicleanphonestorage.app.ad.AdSdkInitializer
import com.example.aicleanphonestorage.feature.settings.AdDebugActivity
import com.example.aicleanphonestorage.feature.settings.AdDebugPreviewActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 显式 opt-in 才请求一次实际 SDK 广告；不点击创意，且始终恢复之前的来源/广告总开关。 */
@RunWith(AndroidJUnit4::class)
class AdDebugLivePreviewTest {
    @get:Rule val noHotAds = NoHotStartAdsRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun adMobNativeRequestReturnsAnActualSdkOutcomeThroughThePreviewPage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("adDebugLivePreview") == "true")
        val original = runBlocking(Dispatchers.IO) {
            AdSourceController.getCurrentSource() to GlobalAdSwitchInterceptor.isGlobalAdEnabled()
        }
        try {
            runBlocking(Dispatchers.IO) {
                AdSourceController.setCurrentSource(AdSource.ADMOB)
                GlobalAdSwitchInterceptor.enableGlobalAd()
            }
            ActivityScenario.launch(AdDebugActivity::class.java).use { scenario ->
                val ready = runBlocking {
                    withTimeout(60_000L) { AdSdkInitializer.state.first {
                        it == AdInitializationState.READY || it == AdInitializationState.FAILED
                    } }
                }
                assertEquals("SDK must finish initialization before this live test", AdInitializationState.READY, ready)
                waitUntil {
                    var editable = false
                    scenario.onActivity { editable = it.findViewById<Spinner>(R.id.ad_debug_format).isEnabled }
                    editable
                }
                scenario.onActivity { it.findViewById<Spinner>(R.id.ad_debug_format).setSelection(4) }
                waitUntil {
                    var enabled = false
                    scenario.onActivity {
                        enabled = it.findViewById<Spinner>(R.id.ad_debug_format).selectedItemPosition == 4 &&
                            it.findViewById<View>(R.id.ad_debug_show).isEnabled
                    }
                    enabled
                }
                scenario.onActivity { it.findViewById<View>(R.id.ad_debug_show).performClick() }
                var outcome = ""
                waitUntil(120_000L) {
                    instrumentation.runOnMainSync {
                        val preview = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<AdDebugPreviewActivity>().firstOrNull()
                        val value = preview?.findViewById<TextView>(R.id.ad_debug_preview_result)?.text?.toString().orEmpty()
                        if (value.startsWith("SDK ") || value.startsWith("Preview ")) outcome = value
                    }
                    outcome.isNotBlank()
                }
                assertFalse("Preview must wait for window focus before requesting", outcome.contains("not in the foreground"))
                val folder = File(context.getExternalFilesDir(null), "ad-debug-tests").apply { mkdirs() }
                File(folder, "live-preview-result.txt").writeText(outcome)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try { File(folder, "live-admob-native.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    } } finally { bitmap.recycle() }
                }
                instrumentation.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<AdDebugPreviewActivity>().forEach { it.finish() }
                }
                waitUntil {
                    var returned = false
                    scenario.onActivity { returned = it.findViewById<TextView>(R.id.ad_debug_result).text.toString() == outcome }
                    returned
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                for (stage in listOf(Stage.RESUMED, Stage.STARTED, Stage.STOPPED)) {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                        .filter { it is AdDebugActivity || it is AdDebugPreviewActivity }.forEach { it.finish() }
                }
            }
            instrumentation.waitForIdleSync()
            runBlocking(Dispatchers.IO) {
                AdSourceController.setCurrentSource(original.first)
                if (original.second) GlobalAdSwitchInterceptor.enableGlobalAd() else GlobalAdSwitchInterceptor.disableGlobalAd()
            }
        }
    }

    private fun waitUntil(timeout: Long = 15_000L, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100L)
        assertTrue("Ad debug UI did not settle", condition())
    }
}
