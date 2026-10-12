package com.example.aicleanphonestorage.feature.speedtest.ui

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogSpeedTestFailedBinding
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException

/**
 * Figma 6565:1676 测速失败弹框。只回传 retry/cancel 小结果，由页面 ViewModel 决定重测或关闭；
 * FragmentManager 负责重建恢复。返回键等同 Cancel。
 */
class SpeedTestFailureDialog : DialogFragment() {
    private val reason: SpeedTestException.Reason
        get() = SpeedTestException.Reason.valueOf(requireArguments().getString(REASON).orEmpty())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSpeedTestFailedBinding.inflate(layoutInflater)
        binding.failedMessage.setText(
            if (reason == SpeedTestException.Reason.NoInternet) R.string.speed_test_no_internet else R.string.speed_test_failed
        )
        binding.failedCancel.setOnClickListener { result(CANCEL); dismiss() }
        binding.failedRetry.setOnClickListener { result(RETRY); dismiss() }
        stackLongActions(binding)
        return Dialog(requireContext()).apply {
            setContentView(binding.root)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            val density = resources.displayMetrics.density
            setLayout(
                minOf((315 * density).toInt(), resources.displayMetrics.widthPixels - (48 * density).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.7f }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        result(CANCEL)
        super.onCancel(dialog)
    }

    /** 大字号或长译文放不下并排按钮时改为纵排，主操作在上，文字不截断。 */
    private fun stackLongActions(binding: DialogSpeedTestFailedBinding) {
        val configuration = resources.configuration
        if (configuration.fontScale <= 1.3f && configuration.screenWidthDp >= 340) return
        binding.failedActions.orientation = LinearLayout.VERTICAL
        binding.failedActions.removeAllViews()
        listOf(binding.failedRetry, binding.failedGap, binding.failedCancel).forEach { view ->
            binding.failedActions.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        binding.failedGap.layoutParams.height = (8 * resources.displayMetrics.density).toInt()
    }

    private fun result(action: String) =
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString(ACTION, action) })

    companion object {
        const val TAG = "speed_test.failure"
        const val RESULT = "speed_test.failure.result"
        const val ACTION = "action"
        const val RETRY = "retry"
        const val CANCEL = "cancel"
        private const val REASON = "reason"

        fun create(reason: SpeedTestException.Reason) =
            SpeedTestFailureDialog().apply { arguments = Bundle().apply { putString(REASON, reason.name) } }
    }
}
