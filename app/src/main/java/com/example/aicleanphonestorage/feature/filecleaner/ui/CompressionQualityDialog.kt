package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogCompressionQualityBinding

/** 品质选择仅发送文件 ID/品质，不持有 Activity 回调、照片或索引；旋转后由 Fragment 恢复。 */
class CompressionQualityDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogCompressionQualityBinding.inflate(inflater, container, false)
        ViewCompat.setAccessibilityHeading(binding.qualityTitle, true)
        val buttons = listOf(binding.qualitySmall, binding.qualityBalanced, binding.qualityHigh)
        val options = QualityOption.entries
        options.zip(buttons).forEach { (option, button) -> bind(button, option) }
        val selected =
            options
                .indexOfFirst { it.value == requireArguments().getInt(QUALITY) }
                .takeIf { it >= 0 } ?: 1
        // 仅明确点击才发出结果；系统恢复 RadioGroup 状态不会触发保存/关闭。
        binding.qualityOptions.check(buttons[selected].id)
        var delivered = false
        buttons.forEachIndexed { position, button ->
            button.setOnClickListener {
                if (delivered) return@setOnClickListener
                delivered = true
                if (options[position].value != requireArguments().getInt(QUALITY))
                    parentFragmentManager.setFragmentResult(
                        RESULT,
                        Bundle().apply {
                            putLong(FILE_ID, requireArguments().getLong(FILE_ID))
                            putInt(QUALITY, options[position].value)
                        },
                    )
                dismiss()
            }
        }
        binding.qualityCancel.setOnClickListener { dismiss() }
        return binding.root
    }

    private fun bind(button: RadioButton, option: QualityOption) {
        val indicator = ContextCompat.getDrawable(button.context, R.drawable.quality_check_selector)
        val size = (20 * button.resources.displayMetrics.density).toInt()
        indicator?.setBounds(0, 0, size, size)
        button.setCompoundDrawablesRelative(null, null, indicator, null)
        val title = button.context.getString(option.title)
        val description = button.context.getString(option.description)
        button.text =
            SpannableString("$title\n$description").apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(
                    RelativeSizeSpan(0.857143f),
                    title.length + 1,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(button.context, R.color.home_text_secondary)
                    ),
                    title.length + 1,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width =
            minOf(
                    (288 * metrics.density).toInt(),
                    metrics.widthPixels - (48 * metrics.density).toInt(),
                )
                .coerceAtLeast(1)
        val maxHeight = (metrics.heightPixels - (80 * metrics.density).toInt()).coerceAtLeast(1)
        requireView()
            .measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
            )
        dialog?.window?.apply {
            setLayout(width, requireView().measuredHeight)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }
    }

    companion object {
        const val TAG = "cleanup.quality.dialog"
        const val RESULT = "cleanup.quality.result"
        const val FILE_ID = "file"
        const val QUALITY = "quality"

        fun create(fileId: Long, quality: Int) =
            CompressionQualityDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(FILE_ID, fileId)
                        putInt(QUALITY, quality)
                    }
            }
    }
}

internal enum class QualityOption(val value: Int, val title: Int, val description: Int) {
    SMALL(60, R.string.quality_small_title, R.string.quality_small_description),
    BALANCED(75, R.string.quality_balanced_title, R.string.quality_balanced_description),
    HIGH(85, R.string.quality_high_title, R.string.quality_high_description),
}
