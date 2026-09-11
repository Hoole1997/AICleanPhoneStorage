package com.example.aicleanphonestorage

import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.ad.NativeAdCoordinator
import com.example.aicleanphonestorage.app.ad.NativeAdPlacements
import com.example.aicleanphonestorage.core.ui.loading.LoadingUiState
import com.example.aicleanphonestorage.core.ui.loading.TaskLoadingDialogFragment
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用模拟广告/请求验证弹框生命周期与异步撑高，不依赖真实 SDK 填充。 */
@RunWith(AndroidJUnit4::class)
class ScanDialogNativeAdTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun pendingRequestIsCancelledWhenDialogClosesWhileActivityRemainsVisible() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val pending = Job()
            var calls = 0
            lateinit var dialog: TaskLoadingDialogFragment
            lateinit var container: ViewGroup
            scenario.onActivity { activity ->
                dialog =
                    TaskLoadingDialogFragment.newInstance(
                        LoadingUiState(101, "Scanning", "Preparing files", 0, showAd = false)
                    )
                dialog.showNow(activity.supportFragmentManager, "scan-native-owner-test")
                container = dialog.requireView().findViewById(R.id.loading_ad)
                assertEquals(View.GONE, container.visibility)
                NativeAdCoordinator(
                    activity,
                    container,
                    NativeAdPlacements.SCAN_DIALOG,
                    lifecycleOwner = dialog.viewLifecycleOwner,
                ) { key, _, _ ->
                    assertEquals("native_scanning", key)
                    calls++
                    pending
                }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, calls)
                repeat(3) {
                    dialog.render(
                        LoadingUiState(101, "Scanning", "Preparing files", it * 20, showAd = false)
                    )
                }
                assertEquals(1, calls)
                dialog.dismissNow()
                assertTrue(pending.isCancelled)
                assertEquals(0, container.childCount)
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }
    }

    @Test
    fun asyncAdExpandsWindowAndHiddenAdRestoresCompactHeight() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var dialog: TaskLoadingDialogFragment
            var before = 0
            scenario.onActivity { activity ->
                dialog =
                    TaskLoadingDialogFragment.newInstance(
                        LoadingUiState(102, "Scanning", "Reviewing files", 35, showAd = false)
                    )
                dialog.showNow(activity.supportFragmentManager, "scan-native-size-test")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                before = dialog.requireView().height
                val slot = dialog.requireView().findViewById<ViewGroup>(R.id.loading_ad)
                assertEquals(View.GONE, slot.visibility)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, slot.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, slot.layoutParams.height)
                slot.addView(
                    TextView(activity).apply {
                        text = "native_scanning (test fixture)"
                        gravity = android.view.Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.DKGRAY)
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (96 * activity.resources.displayMetrics.density).toInt(),
                    ),
                )
                slot.visibility = View.VISIBLE // 模拟 loadNative 填充，而不是页面业务主动显示真实广告。
            }
            await {
                var expanded = false
                scenario.onActivity { expanded = dialog.requireView().height > before }
                expanded
            }
            scenario.onActivity { activity ->
                val slot = dialog.requireView().findViewById<ViewGroup>(R.id.loading_ad)
                assertEquals(dialog.requireView().width, slot.width)
                assertTrue(
                    dialog.requireView().height <=
                        activity.resources.displayMetrics.heightPixels -
                            80 * activity.resources.displayMetrics.density
                )
            }
            instrumentation.uiAutomation.takeScreenshot()?.let { image ->
                val dir =
                    File(
                            instrumentation.targetContext.getExternalFilesDir(null),
                            "scan-dialog-tests",
                        )
                        .apply { mkdirs() }
                File(dir, "native_scanning_fixture.png").outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            scenario.onActivity {
                dialog.render(
                    LoadingUiState(102, "Scanning", "Reviewing files", 50, showAd = false)
                )
            }
            await {
                var collapsed = false
                scenario.onActivity { collapsed = dialog.requireView().height == before }
                collapsed
            }
            scenario.onActivity { dialog.dismissNow() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue(condition())
    }
}
