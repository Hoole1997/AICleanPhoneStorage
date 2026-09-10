package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.FrameMetrics
import android.view.View
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.common.bill.ui.dialog.ADLoadingDialog
import com.airbnb.lottie.LottieCompositionFactory
import com.example.aicleanphonestorage.app.ad.renderer.AdLoadingAnimationView
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.lxj.xpopup.core.BasePopupView
import java.io.File
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 只打开 SDK 的 loading，不请求广告。动效设置测试仅在模拟器运行，并恢复系统原值。 */
@RunWith(AndroidJUnit4::class)
class AdLoadingAnimationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun compactCardKeepsLargeTextAndCloseTargetClear() {
        val context = instrumentation.targetContext
        val composition = requireNotNull(LottieCompositionFactory.fromRawResSync(context, R.raw.ad_loading_tiles).value)
        instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply { fontScale = 2f }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val card = LayoutInflater.from(themed).inflate(R.layout.layout_ad_loading, null)
            val animation = card.findViewById<AdLoadingAnimationView>(R.id.ad_loading_progress)
            animation.setComposition(composition)
            val label = card.findViewById<TextView>(R.id.tv_loading_text)
            label.text = "Loading advertisement, please wait…"
            val width = (200 * themed.resources.displayMetrics.density).toInt()
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val close = card.findViewById<View>(R.id.ad_loading_close)
            assertEquals(width, card.width)
            assertTrue(close.right <= card.width)
            assertTrue(close.bottom <= animation.top)
            assertTrue(label.height >= label.layout.height)
            assertTrue(close.width >= (48 * themed.resources.displayMetrics.density).toInt())
            val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
            card.draw(Canvas(bitmap))
            val directory = File(context.getExternalFilesDir(null), "ad-loading-lottie").apply { mkdirs() }
            File(directory, "large-font.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            animation.release()
        }
    }

    @Test fun sdkPopupUsesCompactWidthAndStopsMotionOnBackgroundCloseAndReducedMotion() {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val resolver = instrumentation.targetContext.contentResolver
        val originalScale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        try {
            setScale("1")
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                var ready = false
                scenario.onActivity { activity ->
                    activity.lifecycleScope.launch { ADLoadingDialog.show(activity); ready = true }
                }
                waitUntil { var value = false; instrumentation.runOnMainSync { value = ready }; value }
                lateinit var animation: AdLoadingAnimationView
                lateinit var card: View
                onView(withId(R.id.ad_loading_content)).check { view, error ->
                    if (error != null) throw error
                    card = view
                    val expected = (248 * view.resources.displayMetrics.density).toInt()
                    assertEquals("Actual SDK popup width", expected, view.width)
                }
                onView(withId(R.id.ad_loading_progress)).check { view, error ->
                    if (error != null) throw error
                    animation = view as AdLoadingAnimationView
                }
                waitUntil { var value = false; instrumentation.runOnMainSync { value = animation.isAnimating }; value }
                instrumentation.runOnMainSync {
                    assertNotNull(animation.composition)
                    assertTrue(animation.composition!!.warnings.isEmpty())
                    assertTrue(animation.composition!!.images.isEmpty())
                }

                val timings = mutableListOf<Long>()
                lateinit var window: Window
                val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                    timings += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                }
                instrumentation.runOnMainSync {
                    var parent = animation.parent
                    while (parent !is BasePopupView) parent = requireNotNull(parent.parent)
                    window = parent.hostWindow
                    window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
                }
                SystemClock.sleep(1800)
                instrumentation.runOnMainSync {
                    window.removeOnFrameMetricsAvailableListener(listener)
                    println("Loading frame samples=${timings.size}, meanMs=${timings.average() / 1_000_000}")
                }

                // 使用 Android 实际绘制器逐帧导出一个循环，供视觉核验和动态预览；不保存大 Bitmap 列表。
                instrumentation.runOnMainSync {
                    animation.pauseAnimation()
                    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ad-loading-lottie").apply { mkdirs() }
                    for (frame in 0 until 36) {
                        animation.progress = frame / 36f
                        val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
                        card.draw(Canvas(bitmap))
                        File(directory, "frame_%02d.png".format(frame)).outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                setScale("0")
                waitUntil { var value = false; instrumentation.runOnMainSync { value = !animation.isAnimating && animation.progress == 0f }; value }
                setScale("1")
                waitUntil { var value = false; instrumentation.runOnMainSync { value = animation.isAnimating }; value }
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync { assertFalse(animation.isAnimating) }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitUntil { var value = false; instrumentation.runOnMainSync { value = animation.isAnimating }; value }
                onView(withId(R.id.ad_loading_close)).check { view, error ->
                    if (error != null) throw error
                    assertTrue("SDK must bind the close callback", view.hasOnClickListeners())
                }
                // 仅测试等待 ActivityScenario 的系统转场结束，避免触摸落在尚未移除的过渡窗口上。
                SystemClock.sleep(500)
                onView(withId(R.id.ad_loading_close)).perform(click())
                var closeState = ""
                waitUntil(message = { closeState }) {
                    var value = false
                    instrumentation.runOnMainSync {
                        value = !animation.isAnimating && !ADLoadingDialog.isShowing()
                        closeState = "animating=${animation.isAnimating}, attached=${animation.isAttachedToWindow}, " +
                            "focus=${animation.hasWindowFocus()}, showing=${ADLoadingDialog.isShowing()}, window=${animation.windowVisibility}"
                    }
                    value
                }
            }
        } finally {
            instrumentation.runOnMainSync { ADLoadingDialog.hide() }
            setScale(originalScale)
        }
    }

    private fun setScale(value: String?) {
        val command = if (value == null) "settings delete global animator_duration_scale"
            else "settings put global animator_duration_scale $value"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun waitUntil(message: () -> String = { "Loading animation lifecycle timed out" }, check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue(message(), check())
    }
}
