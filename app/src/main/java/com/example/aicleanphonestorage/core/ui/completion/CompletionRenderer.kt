package com.example.aicleanphonestorage.core.ui.completion

import android.text.format.Formatter
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding
import java.text.NumberFormat

/** 仅将真实结果映射为文案和可见状态，可独立验证字号/屏幕适配。 */
internal class CompletionRenderer(private val binding: ScreenCompletionBinding) {
    private val sizes = com.example.aicleanphonestorage.core.format.StorageSizeFormatter(binding.root.context)
    fun render(value: CompletionReport) =
        with(binding.root.context) {
            binding.completionPageTitle.setText(
                if (value.celebrate) R.string.completion_page_title
                else R.string.completion_result_title
            )
            binding.completionCount.text = NumberFormat.getIntegerInstance().format(value.completed)
            val units =
                when (value.kind) {
                    CompletionKind.CLEANUP -> R.plurals.completion_files
                    CompletionKind.COMPRESSION -> R.plurals.completion_photos
                    CompletionKind.NOTIFICATIONS -> R.plurals.completion_apps
                }
            binding.completionUnit.text = resources.getQuantityString(units, value.completed)
            binding.completionTitle.setText(
                when {
                    !value.successful -> R.string.completion_no_changes_title
                    value.partial -> R.string.completion_partial_title
                    value.kind == CompletionKind.COMPRESSION ->
                        R.string.completion_compression_title
                    value.kind == CompletionKind.NOTIFICATIONS ->
                        R.string.completion_notifications_title
                    else -> R.string.completion_cleanup_title
                }
            )
            val detail = buildList {
                if (!value.successful) add(getString(R.string.completion_no_changes_message))
                if (value.kind == CompletionKind.NOTIFICATIONS)
                    add(
                        getString(
                            if (value.completed == 0) R.string.completion_notifications_off
                            else R.string.completion_notifications_message
                        )
                    )
                if (value.freedBytes > 0)
                    add(
                        getString(
                            R.string.completion_reclaimed,
                            Formatter.formatShortFileSize(binding.root.context, value.freedBytes),
                        )
                    )
                if (value.kind == CompletionKind.COMPRESSION) {
                    value.inputBytes?.let { add(getString(R.string.compression_selected_size, sizes.megabytes(it))) }
                }
                if (value.kind == CompletionKind.COMPRESSION && value.completed > 0) {
                    if (value.partial) value.copiedOriginalBytes?.let {
                        add(getString(R.string.compression_processed_size, sizes.megabytes(it)))
                    }
                    value.outputBytes?.let { add(getString(R.string.compression_output_size, sizes.megabytes(it))) }
                    add(
                        getString(
                            R.string.completion_copies_smaller,
                            sizes.megabytes(value.reducedBytes),
                        )
                    )
                    if (value.originalsRemaining > 0)
                        add(
                            resources.getQuantityString(
                                R.plurals.completion_originals_remaining,
                                value.originalsRemaining,
                                value.originalsRemaining,
                            )
                        )
                }
            }
            binding.completionDetail.isVisible = detail.isNotEmpty()
            binding.completionDetail.text = detail.joinToString("\n\n")
            binding.completionExceptions.isVisible = value.failed > 0 || value.skipped > 0
            binding.completionExceptions.text =
                getString(
                    R.string.completion_exceptions,
                    resources.getQuantityString(
                        R.plurals.completion_skipped,
                        value.skipped,
                        value.skipped,
                    ),
                    resources.getQuantityString(
                        R.plurals.completion_failed,
                        value.failed,
                        value.failed,
                    ),
                )
            binding.completionOriginals.isVisible =
                value.removableOriginals > 0 && value.operationId > 0
        }
}
