package com.example.aicleanphonestorage

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.databinding.ItemHomeHeroBinding
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMotionDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun borderShimmerKeepsCenterClearAndDoesNotInterceptClick() {
        var before: Bitmap? = null
        var shining: Bitmap? = null
        var restored: Bitmap? = null
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Theme_AICleanPhoneStorage)
            val binding = ItemHomeHeroBinding.inflate(LayoutInflater.from(themed))
            binding.summaryTitle.text = "Storage Used"
            binding.summaryValue.text = "40.3"
            binding.summaryUnit.text = "GB"
            binding.scanStatus.text = "of 128 GB"
            binding.usageLabel.text = "31% used"
            val width = (375 * themed.resources.displayMetrics.density).toInt()
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            binding.root.layout(0, 0, width, binding.root.measuredHeight)
            val button = binding.cleanButton
            fun frame(): Bitmap =
                Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888).also {
                    button.draw(Canvas(it))
                }
            before = frame()
            val oldWidth = button.width
            val oldHeight = button.height
            button.setShimmerProgress(0.5f)
            shining = frame()
            assertEquals(oldWidth, button.width)
            assertEquals(oldHeight, button.height)
            var clicked = 0
            button.setOnClickListener { clicked++ }
            button.performClick()
            assertEquals(1, clicked)
            button.setShimmerProgress(null)
            restored = frame()
        }
        try {
            assertFalse("Light band should be visible", before!!.sameAs(shining!!))
            val w = before!!.width
            val h = before!!.height
            for (y in h / 3 until 2 * h / 3) for (x in w / 3 until 2 * w / 3) assertEquals(
                "Border effect must leave text area unchanged",
                before!!.getPixel(x, y),
                shining!!.getPixel(x, y),
            )
            assertTrue("Stopping shimmer restores the original button", before!!.sameAs(restored!!))
            assertEquals(
                "Highlight must not spill outside pill",
                before!!.getPixel(0, 0),
                shining!!.getPixel(0, 0),
            )
            val folder =
                File(context.getExternalFilesDir(null), "home-motion-tests").apply { mkdirs() }
            for ((name, image) in
                listOf("button-static" to before!!, "button-shimmer" to shining!!)) File(
                    folder,
                    "$name.png",
                )
                .outputStream()
                .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            before?.recycle()
            shining?.recycle()
            restored?.recycle()
        }
    }

    @Test
    fun robotStopsInBackgroundAndWhenHeroLeavesViewport() {
        // 不解锁手机、不修改系统动画/省电设置。环境不满足时保留给手动测试。
        assumeTrue(context.getSystemService(PowerManager::class.java).isInteractive)
        assumeTrue(!context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        assumeTrue(!context.getSystemService(PowerManager::class.java).isPowerSaveMode)
        assumeTrue(
            !context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        )
        if (Build.VERSION.SDK_INT >= 26) assumeTrue(ValueAnimator.areAnimatorsEnabled())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var robot: View? = null
            fun moving(): Boolean {
                var moving = false
                scenario.onActivity {
                    robot = it.findViewById(R.id.robot_art)
                    moving = (robot?.translationY ?: 0f) < -0.05f
                }
                return moving
            }
            waitUntil(::moving)
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync {
                assertEquals(0f, robot!!.translationY)
                assertEquals(0f, robot!!.rotation)
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { assertEquals(0f, robot!!.translationY) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil(::moving)
            val oldRobot = robot!!
            scenario.onActivity {
                it.findViewById<RecyclerView>(R.id.home_list).apply {
                    scrollToPosition(adapter!!.itemCount - 1)
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(0f, oldRobot.translationY)
                assertEquals(0f, oldRobot.rotation)
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 8000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(40)
        assertTrue("Motion did not reach expected state", condition())
    }
}
