package com.example.aicleanphonestorage.feature.filecleaner.ui

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** Paging 的初始 NotLoading 不等于成功读到零条；必须等首批页面实际提交后才能显示空布局。 */
internal class CleanupListStateRenderer(private val binding: ScreenFileCleanupBinding) {
    private val values = CleanupValueFormatter(binding.root.context)
    private val screenshots = ScreenshotCleanupRenderer(binding, values)
    private val action = CleanupActionRenderer(binding, values)
    private var presented = false
    private var refresh: LoadState = LoadState.Loading
    private var count = 0
    private var state = CleanupUiState()

    fun state(value: CleanupUiState) {
        state = value
        render()
    }

    fun loading(value: CombinedLoadStates, itemCount: Int) {
        refresh = value.source.refresh
        count = itemCount
        if (refresh is LoadState.Loading) presented = false
        render()
    }

    fun pagesPresented(itemCount: Int) {
        presented = true
        count = itemCount
        render()
    }

    private fun render() {
        val empty =
            state.handle != null && presented && refresh is LoadState.NotLoading && count == 0
        val failed = refresh is LoadState.Error && count == 0
        val detail = state.handle?.feature == CleanupFeature.SMART_CLEAN ||
            (state.handle?.feature == CleanupFeature.UNUSED_FILES && state.filter.bucket != null)
        screenshots.render(state)
        action.render(state)
        if (state.handle?.feature == CleanupFeature.PHOTO_COMPRESS) {
            val selectedSize = if (state.totalsReady) values.megabytes(state.totals.selectedBytes)
                else binding.root.context.getString(R.string.home_unknown_value)
            if (binding.cleanupPotential.text.toString() != selectedSize) binding.cleanupPotential.text = selectedSize
        }
        // 垃圾分类有数据时沿用白底详情；空时使用 Figma 的统一渐变空页，不留无效选择/说明。
        binding.cleanupBackground.isVisible = !detail || empty
        binding.root.setBackgroundColor(
            ContextCompat.getColor(
                binding.root.context,
                if (detail && !empty) R.color.home_surface else R.color.home_background,
            )
        )
        if (detail)
            binding.cleanupScope.isVisible = !empty && !failed && state.filter.bucket != null
        binding.cleanupEmpty.isVisible = empty
        binding.cleanupError.isVisible = failed
        binding.cleanupFiles.isVisible = !empty && !failed
        binding.cleanupProgress.isVisible = refresh is LoadState.Loading && count == 0
        binding.cleanupFooter.isVisible =
            !detail && count > 0
        binding.cleanupPhotoHeader.isVisible =
            state.handle?.feature == CleanupFeature.PHOTO_COMPRESS && !empty && !failed
        binding.cleanupSelectAll.isVisible = !empty && !failed
    }
}
