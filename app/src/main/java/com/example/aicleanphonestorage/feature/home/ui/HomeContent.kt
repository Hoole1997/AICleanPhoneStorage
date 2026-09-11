package com.example.aicleanphonestorage.feature.home.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeToolMetric
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

/** 只包含渲染需要的轻量值，不携带文件列表、Context 或 Bitmap。预览数据也使用同一 UI 契约。 */
data class HomeContent(
    val hero: HeroContent,
    val statistics: HomeStatistics,
    val tools: List<HomeToolItem> = HomeTool.entries.map { HomeToolItem(it) },
)

data class HeroContent(
    val scanComplete: Boolean = false,
    val virtualJunk: Boolean = false,
    val value: String? = null,
    val unit: String = "",
    val totalCapacity: String? = null,
    val usedPercent: Int? = null,
    val progressFraction: Float? = usedPercent?.div(100f),
)

data class HomeStatistics(
    val download: String? = null,
    val available: String? = null,
    val used: String? = null,
)

/** 文案、原版图标集中定义，卡片渲染和网格测量共用，避免两份映射发生偏差。 */
enum class HomeTool(@param:StringRes val titleRes: Int, @param:DrawableRes val iconRes: Int) {
    Network(R.string.home_tool_network, R.drawable.ic_tool_network),
    Notifications(R.string.home_tool_notifications, R.drawable.ic_tool_notifications),
    Apps(R.string.home_tool_apps, R.drawable.ic_tool_apps),
    Compress(R.string.home_tool_compress, R.drawable.ic_tool_compress),
    LargeFiles(R.string.home_tool_large_files, R.drawable.ic_tool_large_files),
    UnusedFiles(R.string.home_tool_unused_files, R.drawable.ic_tool_unused_files),
    Screenshots(R.string.home_tool_screenshots, R.drawable.ic_tool_screenshots),
}

data class HomeToolItem(
    val tool: HomeTool,
    val detail: String? = null,
    val metric: HomeToolMetric? = null,
)

/** 存储与入口摘要保持真实值；主卡单独读取用户要求的虚拟清理展示状态，不写回真实扫描结果。 */
internal fun HomeOverview.toHomeContent(locale: Locale, cleaning: HomeCleaningSnapshot = HomeCleaningSnapshot()): HomeContent {
    val numbers =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
            isGroupingUsed = false
        }
    fun size(bytes: Long): Pair<String, String> {
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1000 && index < units.lastIndex) {
            value /= 1000
            index++
        }
        return numbers.format(value) to units[index]
    }
    fun compact(bytes: Long) = size(bytes).let { it.first + it.second }
    val percent = storage?.let { floor(it.usedFraction * 100).toInt().coerceIn(0, 100) }
    // 首页展示不再取真实扫描索引；清理详情仍使用原来的真实数据源。
    val displayedSize = cleaning.junkNumber(locale)?.let { it to "MB" } ?: storage?.usedBytes?.let(::size)
    return HomeContent(
        hero =
            HeroContent(
                virtualJunk = cleaning.dirty,
                value = displayedSize?.first,
                unit = displayedSize?.second.orEmpty(),
                totalCapacity = storage?.let { compact(it.totalBytes) },
                usedPercent = percent,
                progressFraction = storage?.usedFraction?.toFloat(),
            ),
        tools =
            HomeTool.entries.map { tool ->
                val metric =
                    when (tool) {
                        HomeTool.Network -> tools.network
                        HomeTool.Notifications -> tools.notifications
                        HomeTool.Apps -> tools.apps
                        HomeTool.Compress -> tools.compress
                        HomeTool.LargeFiles -> tools.largeFiles
                        HomeTool.UnusedFiles -> tools.unusedFiles
                        HomeTool.Screenshots -> tools.screenshots
                    }
                HomeToolItem(
                    tool,
                    (metric as? HomeToolMetric.Bytes)?.let {
                        (if (it.partial) "≥" else "") + compact(it.value)
                    },
                    metric,
                )
            },
        statistics =
            HomeStatistics(
                download = wifiBytes?.let(::compact),
                available = storage?.let { compact(it.availableBytes) },
                used = percent?.let { NumberFormat.getPercentInstance(locale).format(it / 100.0) },
            ),
    )
}
