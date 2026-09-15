package com.example.aicleanphonestorage.feature.filecleaner.ui

import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 截图副标题只使用扫描总量，不依赖已加载的分页行或选择状态。 */
internal class ScreenshotCleanupRenderer(
    private val binding: ScreenFileCleanupBinding,
    private val values: CleanupValueFormatter = CleanupValueFormatter(binding.root.context),
) {
    private val context = binding.root.context
    private var displayedTotals: Pair<Int, Long>? = null

    fun render(state: CleanupUiState) {
        val screenshots = state.handle?.feature == CleanupFeature.SCREENSHOTS
        binding.cleanupScreenshotSummary.isVisible = screenshots && state.totalsReady
        if (!screenshots) return
        val totals = state.totals
        val summary = totals.count to totals.bytes
        if (state.totalsReady && displayedTotals != summary) {
            displayedTotals = summary
            // 统计未就绪前保持隐藏，避免把初始 0 误呈现为扫描完成结果。
            binding.cleanupScreenshotSummary.text = context.resources.getQuantityString(
                R.plurals.cleanup_screenshot_summary, totals.count,
                values.count(totals.count), values.megabytes(totals.bytes),
            )
        }
    }
}
