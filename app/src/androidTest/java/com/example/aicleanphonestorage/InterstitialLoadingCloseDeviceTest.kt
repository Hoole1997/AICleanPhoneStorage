package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.common.bill.ui.dialog.ADLoadingDialog
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.app.ad.launchInterstitialRequest
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.databinding.ScreenSpeedTestBinding
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestTelemetry
import com.example.aicleanphonestorage.feature.speedtest.data.*
import com.example.aicleanphonestorage.feature.speedtest.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 真机使用实际 SDK 加载弹框、生产续接器/ViewModel/renderer；测速和广告响应仅为测试夹具。 */
@RunWith(AndroidJUnit4::class)
class InterstitialLoadingCloseDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun closeLoadingRevealsSpeedResultAndAllowsNextTestWithoutWaitingForAd() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val response = CompletableDeferred<Boolean>()
            lateinit var requestJob: Job
            lateinit var ads: InterstitialActions
            lateinit var binding: ScreenSpeedTestBinding
            lateinit var model: SpeedTestViewModel
            var calls = 0
            var wouldShowAd = 0
            var nextClicks = 0
            val slot = InterstitialPlacements.SPEED_TEST_SCAN
            scenario.onActivity { activity ->
                binding = ScreenSpeedTestBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                val renderer = SpeedTestRenderer(binding)
                val result = SpeedTestResult(SpeedTestMetrics(1e6, 5e5, 20.0, 3.0, 4.0), SpeedTestServer("Test fixture", "LOCAL"), "fixture")
                model = SpeedTestViewModel(SpeedTestRepository { flowOf(SpeedTestEvent.Completed(result)) },
                    SpeedTestTelemetry(EventSink { _, _ -> })) { SpeedTestNetwork(SpeedTestNetworkType.Wifi, null, "Test device") }
                model.onForeground()
                assertFalse((model.state.value as SpeedTestUiState.Done).revealed)
                renderer.render(model.state.value)
                binding.speedAgain.setOnClickListener { nextClicks++; model.testAgain() }
                ads = InterstitialActions(activity) { position, callback ->
                    requestJob = activity.launchInterstitialRequest(position, { calls++; callback(it) }) {
                        ADLoadingDialog.show(activity)
                        val success = response.await()
                        wouldShowAd++
                        success
                    }
                }
                ads.register(slot) { generation -> model.revealResult(generation); renderer.render(model.state.value) }
                ads.run(slot, slot, model.generation)
                assertTrue(ads.busy)
            }
            lateinit var close: View
            onView(withId(R.id.ad_loading_close)).check { view, error ->
                if (error != null) throw error
                close = view
            }.perform(click())
            awaitDetached(close)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, calls)
                assertTrue(requestJob.isCancelled)
                assertFalse(ads.busy)
                assertFalse(ForegroundTransitionGuard.contains("interstitial:$slot"))
                assertNull(activity.window.decorView.getTag(R.id.ad_loading_request))
                assertTrue((model.state.value as SpeedTestUiState.Done).revealed)
                assertTrue(binding.speedFooter.isVisible)
                assertFalse(binding.speedTesting.isVisible)
                assertEquals(0, wouldShowAd)
                response.complete(true) // 模拟关闭后网络才返回，旧请求不能继续展示/再次续接。
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, calls)
                assertEquals(0, wouldShowAd)
                val bitmap = Bitmap.createBitmap(binding.root.width, binding.root.height, Bitmap.Config.ARGB_8888)
                binding.root.draw(Canvas(bitmap))
                val folder = File(activity.getExternalFilesDir(null), "speedtest-ad-close").apply { mkdirs() }
                File(folder, "result-after-close.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val generation = model.generation
                assertTrue(binding.speedAgain.performClick())
                assertEquals(1, nextClicks)
                assertEquals(generation + 1, model.generation)
            }
        }
    }

    @Test fun sdkHidingLoadingForPresentationDoesNotReleaseBusinessEarly() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val response = CompletableDeferred<Boolean>()
            var calls = 0
            lateinit var job: Job
            scenario.onActivity { activity ->
                job = activity.launchInterstitialRequest("test-auto-hide", { calls++; assertTrue(it) }) {
                    ADLoadingDialog.show(activity)
                    response.await()
                }
            }
            // 等待 SDK renderer 已建立；仅隐藏弹框不是用户取消，广告实际结束前不能续接。
            lateinit var close: View
            onView(withId(R.id.ad_loading_close)).check { view, error ->
                if (error != null) throw error
                close = view
            }
            scenario.onActivity {
                ADLoadingDialog.hide()
                assertEquals(0, calls)
                assertTrue(job.isActive)
                response.complete(true)
            }
            awaitDetached(close)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, calls)
                assertTrue(job.isCompleted)
                assertNull(activity.window.decorView.getTag(R.id.ad_loading_request))
            }
        }
    }

    /** SDK 的 dismiss 动画异步销毁全局弹框，等旧窗口脱离后才启动下一例，避免污染其 show continuation。 */
    private fun awaitDetached(view: View) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var attached = false
            instrumentation.runOnMainSync { attached = view.isAttachedToWindow }
            if (!attached) return
            SystemClock.sleep(25)
        }
        fail("SDK loading window did not detach")
    }
}
