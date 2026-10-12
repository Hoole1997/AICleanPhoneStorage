package com.example.aicleanphonestorage

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.data.HomeBadge
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.home.ui.HomeBadgeStyle
import com.example.aicleanphonestorage.feature.home.ui.HomeListAdapter
import com.example.aicleanphonestorage.feature.home.ui.HomeRenderer
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.home.ui.HomeUiActions
import com.example.aicleanphonestorage.feature.home.ui.HomeUiState
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页入口角标（Figma 6562:1177）的离屏几何核对：预览数据全部显示角标，不读写真实角标记录。
 * 截图写入应用缓存目录 home-badges/，供与设计稿人工比对。
 */
@RunWith(AndroidJUnit4::class)
class HomeBadgeRenderingTest {
    @Test fun badgesFollowDesignGeometryAcrossLayouts() {
        capture("badges_375", 375, 1400, 1f, 3)
        capture("badges_320_compact", 320, 2400, 1f, 2)
        capture("badges_large_font", 375, 3200, 2f, 1)
    }

    /** 真实 Ready 状态经 HomeRenderer 渲染：只显示仓库给出的角标，其余入口隐藏。 */
    @Test fun readyStateShowsOnlyVisibleBadges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var binding: ScreenHomeBinding
        lateinit var renderer: HomeRenderer
        lateinit var themed: Context
        fun visibleBadges(): Set<String> {
            var result = emptySet<String>()
            instrumentation.runOnMainSync {
                repeat(2) {
                    binding.root.measure(View.MeasureSpec.makeMeasureSpec(375, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
                    binding.root.layout(0, 0, 375, 2400)
                    binding.root.requestLayout()
                }
                val list = binding.homeList
                result = (0 until list.childCount).map { list.getChildViewHolder(list.getChildAt(it)) }.mapNotNull { holder ->
                    val view = holder.itemView
                    when (holder.itemViewType) {
                        HomeListAdapter.HERO -> "hero".takeIf { view.findViewById<View>(R.id.clean_badge).visibility == View.VISIBLE }
                        HomeListAdapter.MALWARE -> "malware".takeIf { view.findViewById<View>(R.id.malware_entry_badge).visibility == View.VISIBLE }
                        HomeListAdapter.TOOL -> view.findViewById<TextView>(R.id.tool_title).text.toString()
                            .takeIf { view.findViewById<View>(R.id.tool_badge).visibility == View.VISIBLE }
                        else -> null
                    }
                }.toSet()
            }
            return result
        }
        // ListAdapter 的后续 diff 在后台计算再提交主线程，这里有界等待提交完成。
        fun awaitBadges(expected: Set<String>) {
            var actual = visibleBadges()
            repeat(100) {
                if (actual == expected) return
                Thread.sleep(20)
                instrumentation.waitForIdleSync()
                actual = visibleBadges()
            }
            assertEquals(expected, actual)
        }
        instrumentation.runOnMainSync {
            val configuration = Configuration(context.resources.configuration).apply {
                screenWidthDp = 375; screenHeightDp = 2400; densityDpi = 160; fontScale = 1f; setLocale(Locale.US)
            }
            themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
            binding = ScreenHomeBinding.inflate(LayoutInflater.from(themed))
            renderer = HomeRenderer(binding, HomeUiActions.None)
            renderer.render(HomeUiState.Ready(HomeOverview(), badges = setOf(HomeBadge.SmartClean, HomeBadge.Videos)))
        }
        awaitBadges(setOf("hero", themed.getString(HomeTool.Videos.titleRes)))
        instrumentation.runOnMainSync { renderer.render(HomeUiState.Ready(HomeOverview(), badges = setOf(HomeBadge.MalwareScan))) }
        awaitBadges(setOf("malware"))
        instrumentation.runOnMainSync {
            // 角标隐藏后按钮恢复原蓝色文字与图标。
            val clean = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.clean_button)
            assertEquals(themed.getColor(R.color.home_blue), clean.currentTextColor)
            assertTrue("blue clean icon restored", sameIcon(clean.icon, themed.getDrawable(R.drawable.ic_home_clean)!!))
        }
        instrumentation.runOnMainSync { renderer.dispose() }
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
            val renderer = HomeRenderer(binding, HomeUiActions.None)
            renderer.render(HomeUiState.Loading, HomePreviewSupport.content("initial"))
            val width = widthDp * scale
            val height = heightDp * scale
            repeat(2) {
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, width, height)
                binding.root.requestLayout()
            }
            fun dp(value: Int) = (value * themed.resources.displayMetrics.density).roundToInt()
            val alert = themed.getDrawable(R.drawable.ic_home_badge_alert)!!.constantState
            val dot = themed.getDrawable(R.drawable.ic_home_badge_dot)!!.constantState
            val list = binding.homeList
            val holders = (0 until list.childCount).map { list.getChildViewHolder(list.getChildAt(it)) }

            // Smart Clean Now：24dp 感叹号，右缘与按钮对齐，顶部高出按钮 10dp。
            val hero = holders.single { it.itemViewType == HomeListAdapter.HERO }.itemView
            val button = hero.findViewById<View>(R.id.clean_button)
            val heroBadge = hero.findViewById<ImageView>(R.id.clean_badge)
            assertEquals(View.VISIBLE, heroBadge.visibility)
            assertEquals(dp(24), heroBadge.width)
            assertEquals("$name hero badge end", button.right, heroBadge.right)
            assertEquals("$name hero badge lift", button.top - dp(10), (heroBadge.top + heroBadge.translationY).roundToInt())
            // 角标显示时文字与图标为 Figma 红色（#F55D46）。
            val clean = button as com.example.aicleanphonestorage.core.ui.motion.ShimmerPillButton
            assertEquals(themed.getColor(R.color.home_alert_red), clean.currentTextColor)
            assertTrue("$name red clean icon", sameIcon(clean.icon, themed.getDrawable(R.drawable.ic_home_clean_alert)!!))

            // Malware Scan：20dp 感叹号，右缘与白色胶囊对齐，顶部高出胶囊可见顶部 10dp，且仍在卡片内。
            val malware = holders.single { it.itemViewType == HomeListAdapter.MALWARE }.itemView
            val scan = malware.findViewById<View>(R.id.malware_entry_scan)
            val malwareBadge = malware.findViewById<ImageView>(R.id.malware_entry_badge)
            assertEquals(View.VISIBLE, malwareBadge.visibility)
            assertSame(alert, malwareBadge.drawable.constantState)
            assertEquals("$name malware badge end", scan.right, malwareBadge.right)
            assertEquals("$name malware badge lift", scan.top + dp(8) - dp(10), malwareBadge.top)
            assertTrue("$name malware badge inside card", offsetIn(malwareBadge, malware).top >= 0)

            // 工具卡片：20dp，距卡片顶部/尾部 10dp；通知与电池为感叹号，其余红点；不遮挡标题。
            val tools = holders.filter { it.itemViewType == HomeListAdapter.TOOL }
            assertTrue("$name must lay out tool cards", tools.isNotEmpty())
            tools.forEach { holder ->
                val card = holder.itemView
                val tool = HomeTool.entries.single { card.context.getString(it.titleRes) == card.findViewById<TextView>(R.id.tool_title).text.toString() }
                val badge = card.findViewById<ImageView>(R.id.tool_badge)
                assertEquals(View.VISIBLE, badge.visibility)
                assertEquals(dp(20), badge.width)
                assertEquals("$name ${tool.name} badge top", dp(10), badge.top)
                assertEquals("$name ${tool.name} badge end", card.width - dp(10), badge.right)
                assertSame(if (tool.badgeStyle == HomeBadgeStyle.Alert) alert else dot, badge.drawable.constantState)
                val title = card.findViewById<View>(R.id.tool_title)
                assertTrue("$name ${tool.name} badge must not cover title", badge.bottom <= offsetIn(title, card).top)
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(themed.getColor(R.color.home_background))
                binding.root.draw(canvas)
            }
            // 固定一帧边框流光（不启动动画），核对角标状态下光带为红色系。
            val shimmer = Bitmap.createBitmap(clean.width, clean.height, Bitmap.Config.ARGB_8888)
            clean.setShimmerProgress(0.12f)
            clean.draw(Canvas(shimmer))
            clean.setShimmerProgress(null)
            File(context.cacheDir, "home-badges").apply { mkdirs() }.let { folder ->
                File(folder, "${name}_shimmer.png").outputStream().use { shimmer.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            shimmer.recycle()
            renderer.dispose()
        }
        val folder = File(context.cacheDir, "home-badges").apply { mkdirs() }
        try {
            File(folder, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap?.recycle()
        }
    }

    // MaterialButton 会 mutate 图标，constantState 不再相同；比较底层原版位图像素。
    private fun sameIcon(actual: android.graphics.drawable.Drawable, expected: android.graphics.drawable.Drawable) =
        (actual as android.graphics.drawable.BitmapDrawable).bitmap.sameAs((expected as android.graphics.drawable.BitmapDrawable).bitmap)

    private fun offsetIn(view: View, ancestor: View): android.graphics.Rect {
        val rect = android.graphics.Rect(0, 0, view.width, view.height)
        (ancestor as ViewGroup).offsetDescendantRectToMyCoords(view, rect)
        return rect
    }
}
