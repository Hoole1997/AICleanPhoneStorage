package com.example.aicleanphonestorage.core.ui.loading

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogTaskLoadingBinding
import java.text.NumberFormat

/** 通用、可恢复的任务展示状态。未知进度为 null，不能用计时器虚构业务完成度。 */
data class LoadingUiState(
    val requestId: Long,
    val title: String,
    val message: String,
    val percent: Int? = null,
    val cancellable: Boolean = true,
    val showAd: Boolean = true,
    val resultKey: String = TaskLoadingDialogFragment.RESULT_KEY,
)

class TaskLoadingDialogFragment : DialogFragment() {
    private var binding: DialogTaskLoadingBinding? = null
    private var spinner: ObjectAnimator? = null
    private lateinit var model: LoadingUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = savedInstanceState ?: requireArguments()
        model = LoadingUiState(state.getLong("id"), state.getString("title").orEmpty(), state.getString("message").orEmpty(),
            state.getInt("percent", -1).takeIf { it >= 0 }, state.getBoolean("cancellable", true), state.getBoolean("ad", true), state.getString("result_key") ?: RESULT_KEY)
        isCancelable = model.cancellable
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = Dialog(requireContext()).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(false)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewBinding = DialogTaskLoadingBinding.inflate(inflater, container, false)
        binding = viewBinding
        viewBinding.loadingClose.setOnClickListener {
            if (model.cancellable) {
                publishCancellation()
                if (!parentFragmentManager.isStateSaved) dismiss()
            }
        }
        render(model)
        return viewBinding.root
    }

    fun render(state: LoadingUiState) {
        model = state
        isCancelable = state.cancellable
        binding?.apply {
            loadingTitle.text = state.title
            loadingMessage.text = state.message
            loadingClose.isVisible = state.cancellable
            loadingAd.isVisible = state.showAd
            if (state.percent == null) {
                if (!loadingProgress.isIndeterminate) {
                    loadingProgress.visibility = View.INVISIBLE
                    loadingProgress.isIndeterminate = true
                    loadingProgress.visibility = View.VISIBLE
                }
                loadingPercentage.setText(R.string.task_loading_unknown)
            } else {
                // 同一帧切换模式并赋值，避免等待 indeterminate 动画结束，导致进度条落后于计数。
                if (loadingProgress.isIndeterminate) loadingProgress.isIndeterminate = false
                loadingProgress.setProgressCompat(state.percent.coerceIn(0, 100), false)
                loadingPercentage.text = NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(state.percent / 100.0)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = minOf((315 * metrics.density).toInt(), metrics.widthPixels - (48 * metrics.density).toInt())
        val maxHeight = metrics.heightPixels - (80 * metrics.density).toInt()
        binding?.root?.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        dialog?.window?.apply {
            setLayout(width, binding?.root?.measuredHeight ?: ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.7f)
        }
        spinner = ObjectAnimator.ofFloat(binding?.loadingSpinner, View.ROTATION, 0f, 360f).apply {
            duration = 1000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onStop() { spinner?.cancel(); spinner = null; super.onStop() }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
    override fun onCancel(dialog: DialogInterface) { publishCancellation(); super.onCancel(dialog) }
    private fun publishCancellation() = parentFragmentManager.setFragmentResult(model.resultKey, Bundle().apply { putLong(REQUEST_ID, model.requestId) })

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(model.toBundle())
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "task_loading"
        const val RESULT_KEY = "task_loading.cancelled"
        const val REQUEST_ID = "request_id"
        fun newInstance(state: LoadingUiState) = TaskLoadingDialogFragment().apply { arguments = state.toBundle() }
        private fun LoadingUiState.toBundle() = Bundle().apply {
            putLong("id", requestId); putString("title", title); putString("message", message)
            putInt("percent", percent ?: -1); putBoolean("cancellable", cancellable); putBoolean("ad", showAd); putString("result_key", resultKey)
        }
    }
}
