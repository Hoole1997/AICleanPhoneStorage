package com.example.aicleanphonestorage

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.databinding.DialogTaskLoadingBinding
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只渲染测试进度，不执行扫描或删除；截图数据与真实扫描完全隔离。 */
@RunWith(AndroidJUnit4::class)
class LoadingCapacityDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun binding(scale: Float = 1f): DialogTaskLoadingBinding {
        val context = instrumentation.targetContext
        val config = Configuration(context.resources.configuration).apply {
            fontScale = scale
            setLocale(Locale.US)
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
        return DialogTaskLoadingBinding.inflate(LayoutInflater.from(themed)).apply {
            loadingTitle.text = "Scanning…"
            loadingMessage.text = "Scanning files"
            loadingCapacity.visibility = View.VISIBLE
        }
    }

    @Test
    fun animationUpdatesCapacityAndBarTogetherAndStopsWhenHidden() {
        val frames = mutableListOf<Pair<Int, Double>>()
        val finished = CountDownLatch(1)
        var animated = false
        lateinit var binding: DialogTaskLoadingBinding
        lateinit var renderer: LoadingCapacityRenderer
        instrumentation.runOnMainSync {
            binding = binding()
            renderer = LoadingCapacityRenderer(binding)
            renderer.render(0, 0, animate = false)
            assertEquals("0.0 MB", binding.loadingCapacity.text.toString())
            assertEquals(0, binding.loadingProgress.progress)
            animated = ValueAnimator.areAnimatorsEnabled()
            renderer.render(200_000_000, 80, animate = true)
            val deadline = SystemClock.uptimeMillis() + 2500
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frames += binding.loadingProgress.progress to
                        binding.loadingCapacity.text.toString().removeSuffix(" MB").toDouble()
                    if (binding.loadingProgress.progress == 8000 || SystemClock.uptimeMillis() >= deadline) {
                        finished.countDown()
                    } else Choreographer.getInstance().postFrameCallback(this)
                }
            }
            Choreographer.getInstance().postFrameCallback(callback)
        }
        assertTrue(finished.await(3, TimeUnit.SECONDS))
        assertEquals(8000, frames.last().first)
        if (animated) assertTrue(frames.any { it.first in 1..7999 })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.first >= a.first && b.second >= a.second })
        frames.forEach { (progress, megabytes) ->
            assertEquals(progress / 8000.0 * 200, megabytes, 0.1)
        }
        var stopped = 0
        instrumentation.runOnMainSync {
            renderer.render(240_000_000, 96, animate = true)
            renderer.stop()
            stopped = binding.loadingProgress.progress
        }
        SystemClock.sleep(150)
        instrumentation.runOnMainSync {
            assertEquals(stopped, binding.loadingProgress.progress)
            renderer.render(250_000_000, 100, animate = false)
            assertEquals("250.0 MB", binding.loadingCapacity.text.toString())
            assertEquals(10_000, binding.loadingProgress.progress)
            renderer.stop()
        }
    }

    @Test
    fun capacitySurvivesDialogRecreation() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                TaskLoadingDialogFragment.newInstance(
                    LoadingUiState(987, "Scanning…", "Scanning files", 50, showAd = false, bytes = 50_000_000),
                ).showNow(it.supportFragmentManager, "capacity.test")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val dialog = it.supportFragmentManager.findFragmentByTag("capacity.test") as TaskLoadingDialogFragment
                val visible = DialogTaskLoadingBinding.bind(dialog.requireView())
                assertTrue(visible.loadingProgress.isShown)
                assertTrue(visible.loadingProgress.progressDrawable?.isVisible == true)
                val image = Bitmap.createBitmap(visible.root.width, visible.root.height, Bitmap.Config.ARGB_8888)
                try {
                    visible.root.draw(Canvas(image))
                    val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "loading-capacity-tests").apply { mkdirs() }
                    File(folder, "attached_dialog_50.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { image.recycle() }
            }
            scenario.recreate()
            scenario.onActivity {
                val dialog = it.supportFragmentManager.findFragmentByTag("capacity.test") as TaskLoadingDialogFragment
                val restored = DialogTaskLoadingBinding.bind(dialog.requireView())
                assertEquals(View.VISIBLE, restored.loadingCapacity.visibility)
                assertTrue(restored.loadingCapacity.text.endsWith(" MB"))
                assertEquals(5000, restored.loadingProgress.progress)
                dialog.dismissNow()
            }
        }
    }

    @Test
    fun capacityFitsSmallScreensAndLargeFonts() {
        for ((widthDp, scale) in listOf(315 to 1f, 272 to 2f)) {
            instrumentation.runOnMainSync {
                val binding = binding(scale)
                val renderer = LoadingCapacityRenderer(binding)
                for (percent in listOf(0, 50, 100)) {
                    renderer.render(12_345_600_000L * percent / 100, percent, animate = false)
                    val width = (widthDp * binding.root.resources.displayMetrics.density).toInt()
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    binding.root.layout(0, 0, width, binding.root.measuredHeight)
                    val text = binding.loadingCapacity
                    assertTrue(text.height >= text.layout.height)
                    for (line in 0 until text.layout.lineCount) assertTrue(text.layout.getLineWidth(line) <= text.width)
                    assertTrue(text.bottom <= binding.loadingMessage.top)
                    val image = Bitmap.createBitmap(width, binding.root.height, Bitmap.Config.ARGB_8888)
                    try {
                        binding.root.draw(Canvas(image))
                        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "loading-capacity-tests").apply { mkdirs() }
                        File(folder, "capacity_${widthDp}_${scale}_$percent.png").outputStream().use {
                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally {
                        image.recycle()
                    }
                }
                renderer.stop()
            }
        }
    }
}
