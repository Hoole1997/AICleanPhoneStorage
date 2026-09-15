package com.example.aicleanphonestorage.feature.filecleaner.ui

import androidx.core.content.ContextCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 底部按钮唯一更新入口：仅更新实际变化的文案/状态，不在每次选中时重设背景。 */
internal class CleanupActionRenderer(
    private val binding: ScreenFileCleanupBinding,
    private val values: CleanupValueFormatter = CleanupValueFormatter(binding.root.context),
) {
    private val context = binding.root.context
    private var selectionStyleApplied = false

    fun render(state: CleanupUiState) {
        val feature = state.handle?.feature
        val selectionLabel = feature == CleanupFeature.PHOTO_COMPRESS || feature == CleanupFeature.SCREENSHOTS
        val selected = state.totalsReady && state.totals.selectedCount > 0
        val label = when {
            feature == CleanupFeature.PHOTO_COMPRESS && selected ->
                context.getString(R.string.cleanup_compress_selected, values.count(state.totals.selectedCount))
            feature == CleanupFeature.PHOTO_COMPRESS -> context.getString(R.string.cleanup_compress)
            feature == CleanupFeature.SCREENSHOTS && selected ->
                context.getString(R.string.cleanup_screenshot_clean, values.megabytes(state.totals.selectedBytes))
            (feature == CleanupFeature.LARGE_FILES || feature == CleanupFeature.UNUSED_FILES) && selected ->
                context.getString(R.string.cleanup_count_size, values.count(state.totals.selectedCount),
                    android.text.format.Formatter.formatFileSize(context, state.totals.selectedBytes))
            else -> context.getString(R.string.cleanup_clean)
        }
        binding.cleanupAction.apply {
            if (selectionLabel && !selectionStyleApplied) {
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.cleanup_selection_action_background)
                setTextColor(ContextCompat.getColorStateList(context, R.color.cleanup_selection_action_text))
                selectionStyleApplied = true
            }
            if (text.toString() != label) text = label
            // 压缩/截图在选择写入期间只拦截点击，保持已选按钮外观；零选择和执行操作时才置灰。
            // prepare() 还会等待总量查询并再次验证选择，不会使用旧的操作快照。
            val enabled = selected && state.operation == CleanupOperationState.Idle &&
                (selectionLabel || state.editing == 0)
            val clickable = enabled && state.editing == 0
            if (isEnabled != enabled) isEnabled = enabled
            if (isClickable != clickable) isClickable = clickable
        }
    }
}
