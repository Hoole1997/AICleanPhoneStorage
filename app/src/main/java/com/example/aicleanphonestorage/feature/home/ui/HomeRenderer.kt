package com.example.aicleanphonestorage.feature.home.ui

import android.text.format.Formatter
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.data.ScanSummary
import java.text.NumberFormat

/** 单向渲染：只格式化几个摘要字段，不触发 I/O 或修改状态。后续还原 Figma 可直接替换此层。 */
internal fun ScreenHomeBinding.renderHome(state: HomeUiState) {
    val context = root.context
    loadingIndicator.isVisible = state is HomeUiState.Loading
    summaryGroup.isVisible = state is HomeUiState.Ready
    errorMessage.isVisible = state is HomeUiState.Failure
    retryButton.isVisible = state is HomeUiState.Failure

    when (state) {
        HomeUiState.Loading -> Unit
        is HomeUiState.Failure -> {
            errorMessage.setText(
                when (state.reason) {
                    HomeUiState.Reason.PermissionRequired -> R.string.home_permission_required
                    HomeUiState.Reason.StorageUnavailable -> R.string.home_storage_unavailable
                },
            )
        }
        is HomeUiState.Ready -> {
            val overview = state.overview
            when (val scan = overview.scan) {
                ScanSummary.NotScanned -> {
                    summaryTitle.setText(R.string.home_storage_used)
                    summaryValue.text = overview.storage?.let {
                        Formatter.formatShortFileSize(context, it.usedBytes)
                    } ?: context.getString(R.string.home_unknown_value)
                    scanStatus.setText(R.string.home_not_scanned)
                }
                is ScanSummary.Completed -> {
                    summaryTitle.setText(R.string.home_scan_complete)
                    summaryValue.text = Formatter.formatShortFileSize(context, scan.junkBytes)
                    scanStatus.setText(R.string.home_junk_found)
                }
            }
            storageStatus.text = overview.storage?.let { storage ->
                context.getString(
                    R.string.home_storage_summary,
                    Formatter.formatShortFileSize(context, storage.availableBytes),
                    NumberFormat.getPercentInstance().format(storage.usedFraction),
                )
            } ?: context.getString(R.string.home_storage_not_loaded)
        }
    }
}
