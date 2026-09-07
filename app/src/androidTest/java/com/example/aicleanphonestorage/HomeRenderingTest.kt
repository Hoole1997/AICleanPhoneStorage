package com.example.aicleanphonestorage

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.home.ui.HomeRenderer
import com.example.aicleanphonestorage.feature.home.ui.HomeUiActions
import com.example.aicleanphonestorage.feature.home.ui.HomeUiState
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实 Android View 的离屏渲染，不改动真机全局分辨率/字号，不把整屏图片打包进应用。
 * 375dp 对齐 Figma 的内容宽度；不伪造系统栏。输出供人工逐像素视觉复核，不声明为性能基准。
 */
@RunWith(AndroidJUnit4::class)
class HomeRenderingTest {
    @Test fun renderBothDesignStatesAndAdaptiveLayouts() {
        capture("initial", 375, 1000, 1f, 3)
        capture("scanned", 375, 1000, 1f, 3)
        capture("phone_360", 360, 1100, 1f, 3)
        capture("phone_360_font_120", 360, 1300, 1.2f, 2)
        capture("compact", 320, 1800, 1f, 2)
        capture("large_font", 375, 2400, 2f, 1)
        capture("wide", 640, 1050, 1f, 2)
    }

    private fun capture(name: String, widthDp: Int, heightDp: Int, fontScale: Float, scale: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val configuration = Configuration(context.resources.configuration).apply {
                screenWidthDp = widthDp
                screenHeightDp = heightDp
                densityDpi = 160 * scale
                this.fontScale = fontScale
                setLocale(Locale.US)
            }
            val themed: Context = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
            val binding = ScreenHomeBinding.inflate(LayoutInflater.from(themed))
            binding.root.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val renderer = HomeRenderer(binding, HomeUiActions.None)
            renderer.render(HomeUiState.Loading, HomePreviewSupport.content(if (name == "scanned") "scanned" else "initial"))
            val width = widthDp * scale
            val height = heightDp * scale
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            // 第二次布局模拟 ViewRoot 的后续遍历，使文字关联的 compound drawable 完成定位。
            binding.root.requestLayout()
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            assertEquals(10, binding.homeList.adapter?.itemCount)
            assertTrue(binding.homeList.childCount > 0)
            val value = binding.root.findViewById<TextView>(R.id.summary_value)
            val unit = binding.root.findViewById<TextView>(R.id.summary_unit)
            assertTrue("Capacity text must fit its measured height", value.height >= value.layout.height)
            assertEquals("Capacity and unit must share a baseline", value.top + value.baseline, unit.top + unit.baseline)
            // 覆盖整组工具而非仅比较相邻两卡，防止第一行变高后与后续工具失去统一排布。
            val cards = (3 until 10).map { position ->
                requireNotNull(binding.homeList.findViewHolderForAdapterPosition(position)).itemView
            }
            val firstTitle = cards.first().findViewById<TextView>(R.id.tool_title)
            val firstDetail = cards.first().findViewById<TextView>(R.id.tool_detail)
            cards.forEach { card ->
                val title = card.findViewById<TextView>(R.id.tool_title)
                val detail = card.findViewById<TextView>(R.id.tool_detail)
                val arrow = card.findViewById<View>(R.id.tool_arrow)
                assertEquals("$name: full title must fit on one line: ${title.text}", 1, title.lineCount)
                assertEquals("Title must not be truncated", 0, title.layout.getEllipsisCount(0))
                assertEquals("Tool cards must have a consistent height", cards.first().height, card.height)
                assertEquals("Title baselines must align within each card", firstTitle.top + firstTitle.baseline, title.top + title.baseline)
                assertEquals("Details must align within each card", firstDetail.top + firstDetail.baseline, detail.top + detail.baseline)
                assertTrue("Arrow must remain below the title", arrow.top >= title.bottom)
            }
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(themed.getColor(R.color.home_background))
                binding.root.draw(canvas)
            }
            renderer.dispose()
        }
        // PNG 压缩/写盘放在 instrumentation 线程，避免测试工具自身在 UI 线程做磁盘 I/O。
        val folder = File(context.cacheDir, "home-rendering").apply { mkdirs() }
        try {
            File(folder, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap?.recycle()
        }
    }
}
