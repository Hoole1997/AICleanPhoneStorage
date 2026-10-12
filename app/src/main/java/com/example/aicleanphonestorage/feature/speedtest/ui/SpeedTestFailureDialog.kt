package com.example.aicleanphonestorage.feature.speedtest.ui

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.annotation.StringRes
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
    private val reason: SpeedTestException.Reason?
        get() = reasonFrom(requireArguments())
    private var actionDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 配置重建后仍保留已处理状态，快速重复点击/取消不能第二次发送 FragmentResult。
        actionDelivered = savedInstanceState?.getBoolean(ACTION_DELIVERED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(ACTION_DELIVERED, actionDelivered)
        super.onSaveInstanceState(outState)
    }

    fun belongsTo(expectedReason: SpeedTestException.Reason?, expectedGeneration: Long): Boolean =
        reason == expectedReason && requireArguments().getLong(GENERATION, -1L) == expectedGeneration

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSpeedTestFailedBinding.inflate(layoutInflater)
        binding.failedMessage.setText(messageRes(reason))
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

    private fun result(action: String) {
        if (actionDelivered) return
        actionDelivered = true
        // 仅传协议原因和轻量代次，不复制参数 Bundle；原因取原弹框，避免下一轮失败覆盖。
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
            putString(ACTION, action)
            putString(REASON, reason?.name)
            putLong(GENERATION, requireArguments().getLong(GENERATION, -1L))
        })
    }

    companion object {
        const val TAG = "speed_test.failure"
        const val RESULT = "speed_test.failure.result"
        const val ACTION = "action"
        const val RETRY = "retry"
        const val CANCEL = "cancel"
        const val GENERATION = "generation"
        private const val REASON = "reason"
        private const val ACTION_DELIVERED = "action_delivered"

        fun reasonFrom(result: Bundle): SpeedTestException.Reason? =
            SpeedTestException.Reason.entries.firstOrNull { it.name == result.getString(REASON) }

        /** 失败原因与文案一一对应（用户 2026-10-12 指定）。 */
        @StringRes
        fun messageRes(reason: SpeedTestException.Reason?): Int = when (reason) {
            SpeedTestException.Reason.NoInternet -> R.string.speed_test_no_internet
            SpeedTestException.Reason.WeakNetwork -> R.string.speed_test_error_weak
            SpeedTestException.Reason.Timeout -> R.string.speed_test_error_timeout
            SpeedTestException.Reason.NetworkChanged -> R.string.speed_test_error_network_changed
            SpeedTestException.Reason.Restricted -> R.string.speed_test_error_restricted
            SpeedTestException.Reason.NetworkInfoUnavailable -> R.string.speed_test_error_network_info
            SpeedTestException.Reason.ServerError -> R.string.speed_test_failed
            null -> R.string.speed_test_no_internet
        }

        fun create(reason: SpeedTestException.Reason?, generation: Long) =
            SpeedTestFailureDialog().apply { arguments = Bundle().apply {
                putString(REASON, reason?.name)
                putLong(GENERATION, generation)
            } }
    }
}
