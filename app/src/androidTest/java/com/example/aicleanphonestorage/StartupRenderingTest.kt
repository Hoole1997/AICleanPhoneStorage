package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.Insets
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding
import com.example.aicleanphonestorage.feature.startup.StartupRenderer
import com.example.aicleanphonestorage.feature.startup.StartupState
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupRenderingTest {
    @Test fun renderFigmaAndAdaptiveNativeLayouts() {
        capture(375, 812, 1f, Insets.NONE)
        capture(360, 800, 1f, Insets.of(0, 24, 0, 24))
        capture(320, 568, 2f, Insets.of(0, 24, 0, 24))
        capture(812, 375, 1f, Insets.of(24, 0, 24, 0))
        capture(640, 360, 2f, Insets.of(24, 0, 24, 0))
    }

    private fun capture(widthDp: Int, heightDp: Int, font: Float, insetDp: Insets) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = Configuration(context.resources.configuration).apply {
            densityDpi = 320; fontScale = font
            screenWidthDp = widthDp; screenHeightDp = heightDp
            orientation = if (widthDp > heightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            setLocale(Locale.US)
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage_Startup)
        // 与 Activity 一样，图片解码不放在 UI 线程。
        val artwork = themed.resources.getDrawable(R.drawable.startup_background, null)
        var bitmap: Bitmap? = null
        val errors = mutableListOf<String>()
        instrumentation.runOnMainSync {
            val binding = ScreenStartupBinding.inflate(LayoutInflater.from(themed))
            binding.startupBackground.setImageDrawable(artwork)
            val renderer = StartupRenderer(binding) { 450 }
            renderer.insets(Insets.of(insetDp.left * 2, insetDp.top * 2, insetDp.right * 2, insetDp.bottom * 2))
            renderer.render(StartupState())
            // 离屏测试没有 onAttachedToWindow，显式展示原生进度 Drawable 以核验实际像素。
            binding.startupProgress.progressDrawable?.setVisible(true, false)
            repeat(2) {
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(widthDp * 2, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightDp * 2, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, widthDp * 2, heightDp * 2)
            }
            for (label in listOf(binding.startupTitle, binding.startupTagline)) {
                assertTrue("$widthDp/$font: text height", label.height >= label.layout.height + label.paddingTop + label.paddingBottom)
                for (line in 0 until label.lineCount) assertEquals(0, label.layout.getEllipsisCount(line))
            }
            assertTrue("$widthDp/$font: brand and footer must not overlap", binding.startupBrand.y + binding.startupBrand.height <= binding.startupFooter.top)
            assertTrue(binding.startupBrand.y >= insetDp.top * 2)
            assertTrue(binding.startupFooter.bottom <= heightDp * 2 - insetDp.bottom * 2)
            assertTrue(binding.startupProgress.width > 0)
            if (widthDp == 375) {
                if (binding.startupTitle.lineCount != 1) errors += "title lines=${binding.startupTitle.lineCount}, width=${binding.startupTitle.width}, text=${binding.startupTitle.paint.measureText(binding.startupTitle.text.toString())}, textSize=${binding.startupTitle.textSize}, brandWidth=${binding.startupBrand.width}, logoWidth=${binding.startupLogo.width}"
                assertEquals(58 * 2, binding.startupLogo.width)
                assertEquals(581f * 2, binding.startupBrand.y, 3f)
            }
            bitmap = Bitmap.createBitmap(widthDp * 2, heightDp * 2, Bitmap.Config.ARGB_8888).also { binding.root.draw(Canvas(it)) }
            renderer.dispose()
        }
        val output = File(context.cacheDir, "startup-rendering").apply { mkdirs() }
        File(output, "${widthDp}_${heightDp}_${font}.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap?.recycle()
        assertTrue(errors.toString(), errors.isEmpty())
    }
}
