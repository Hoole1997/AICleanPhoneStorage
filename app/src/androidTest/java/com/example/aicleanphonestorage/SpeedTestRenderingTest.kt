package com.example.aicleanphonestorage

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.databinding.DialogSpeedTestFailedBinding
import com.example.aicleanphonestorage.databinding.ScreenSpeedTestBinding
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMetrics
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetwork
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetworkType
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestProgress
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestResult
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestServer
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestFailureDialog
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestRenderer
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestUiState
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 测网速页面离屏渲染（Figma 6554:757 / 6554:793 / 6565:1676）。样例数值只存在于测试中，
 * 不进入应用数据源；截图写入应用缓存 speed-test/ 供与设计稿比对。
 */
@RunWith(AndroidJUnit4::class)
class SpeedTestRenderingTest {
    private val network = SpeedTestNetwork(SpeedTestNetworkType.Cellular, "NETVIGATOR", "SM-G986N")
    private val server = SpeedTestServer("Cloudflare", "HKG")
    private val metrics = SpeedTestMetrics(10_554_000.0, 3_860_000.0, 69.0, 69.0, 69.0)

    @Test fun renderTestingResultAndFailure() {
        val testing = SpeedTestUiState.Testing(
            SpeedTestProgress(SpeedTestPhase.Download, 0.42f, SpeedTestMetrics(downloadBytesPerSecond = 8_230_000.0, pingMs = 23.4), server),
            network,
        )
        val done = SpeedTestUiState.Done(SpeedTestResult(metrics, server, "8f2c1a9b3d4e5f60"), network)
        for ((locale, scale) in listOf(Locale.US to 1f, Locale.GERMANY to 1f, Locale.forLanguageTag("ru") to 1.3f, Locale.US to 2f)) {
            val suffix = "${locale.language}_$scale"
            screen("testing_$suffix", testing, locale, scale)
            screen("result_$suffix", done, locale, scale)
            SpeedTestException.Reason.entries.forEach { reason -> dialog("failed_${reason.name}_$suffix", reason, locale, scale) }
        }
    }

    /** 测速成功、等待 scan_complete_speedtest 插屏回调时：保持 100% 测速画面与最终数值，不露出结果页。 */
    @Test fun waitingForScanAdKeepsTestingLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val binding = ScreenSpeedTestBinding.inflate(LayoutInflater.from(themed(Locale.US, 1f, 375, 812)))
            val renderer = SpeedTestRenderer(binding)
            renderer.render(SpeedTestUiState.Done(SpeedTestResult(metrics, server, "ray"), network, revealed = false))
            assertTrue(binding.speedTesting.isShown || binding.speedTesting.visibility == View.VISIBLE)
            assertEquals(View.GONE, binding.speedFooter.visibility)
            assertEquals("100%", binding.speedPercent.text.toString())
            assertEquals("10,554", binding.speedMetrics.metricsDownload.headerValue.text.toString())
            assertEquals(View.GONE, binding.speedDetails.detailsTestId.visibility)
            renderer.render(SpeedTestUiState.Done(SpeedTestResult(metrics, server, "ray"), network, revealed = true))
            assertEquals(View.GONE, binding.speedTesting.visibility)
            assertEquals(View.VISIBLE, binding.speedFooter.visibility)
            assertEquals(View.VISIBLE, binding.speedDetails.detailsTestId.visibility)
        }
    }

    private fun themed(locale: Locale, fontScale: Float, widthDp: Int, heightDp: Int): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            densityDpi = 480
            this.fontScale = fontScale
            setLocale(locale)
        }
        return ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
    }

    private fun screen(name: String, state: SpeedTestUiState, locale: Locale, fontScale: Float) {
        val heightDp = if (fontScale > 1.5f) 1500 else 812
        capture(name, locale, fontScale, 375, heightDp) { context ->
            val binding = ScreenSpeedTestBinding.inflate(LayoutInflater.from(context))
            SpeedTestRenderer(binding).render(state)
            binding.root
        }.also { root ->
            // 数值与单位基线对齐；标签与数值完整显示，不被截断。
            val metric = root.findViewById<View>(R.id.metrics_ping)
            val value = metric.findViewById<TextView>(R.id.metric_value)
            val unit = metric.findViewById<TextView>(R.id.metric_unit)
            assertEquals("$name baseline", value.top + value.baseline, unit.top + unit.baseline)
            allTexts(root).forEach { text ->
                val layout = text.layout ?: return@forEach
                if (text.isShown) assertEquals("$name ellipsized: ${text.text}", 0, (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) })
            }
        }
    }

    private fun dialog(name: String, reason: SpeedTestException.Reason, locale: Locale, fontScale: Float) {
        capture(name, locale, fontScale, 315, 420, transparent = true) { context ->
            DialogSpeedTestFailedBinding.inflate(LayoutInflater.from(context)).apply {
                failedMessage.setText(SpeedTestFailureDialog.messageRes(reason))
            }.root
        }.also { root ->
            val retry = root.findViewById<TextView>(R.id.failed_retry)
            assertTrue("$name retry must fit", retry.layout.getEllipsisCount(0) == 0)
            val message = root.findViewById<TextView>(R.id.failed_message)
            assertEquals("$name message must not be truncated", 0, (0 until message.layout.lineCount).sumOf { message.layout.getEllipsisCount(it) })
            assertTrue("$name message must fit its height", message.height >= message.layout.height)
        }
    }

    private fun capture(
        name: String, locale: Locale, fontScale: Float, widthDp: Int, heightDp: Int,
        transparent: Boolean = false, build: (Context) -> View,
    ): View {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var root: View
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val context = themed(locale, fontScale, widthDp, heightDp)
            root = build(context)
            val width = widthDp * 3
            val height = heightDp * 3
            repeat(2) {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    if (transparent) View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST) else View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, root.measuredHeight)
                root.requestLayout()
            }
            bitmap = Bitmap.createBitmap(width, root.measuredHeight, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(if (transparent) 0xFF4D4D4D.toInt() else context.getColor(R.color.home_background))
                root.draw(canvas)
            }
        }
        val folder = File(instrumentation.targetContext.cacheDir, "speed-test").apply { mkdirs() }
        try {
            File(folder, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap?.recycle()
        }
        return root
    }

    private fun allTexts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { allTexts(view.getChildAt(it)) }
        else -> emptyList()
    }
}
