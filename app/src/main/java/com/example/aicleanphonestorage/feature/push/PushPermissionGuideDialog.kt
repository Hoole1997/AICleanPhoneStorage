package com.example.aicleanphonestorage.feature.push

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogPushPermissionGuideBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** 只展示授权说明并上报选择；系统权限操作由首页协调器执行。 */
class PushPermissionGuideDialog : BottomSheetDialogFragment() {
    private var resultSent = false

    override fun getTheme() = R.style.ThemeOverlay_Clean_PushGuide

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        resultSent = state?.getBoolean("result.sent") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val binding = DialogPushPermissionGuideBinding.inflate(inflater, container, false)
        ViewCompat.setAccessibilityHeading(binding.pushGuideTitle, true)
        binding.pushGuideAllow.setOnClickListener {
            result(true)
            dismiss()
        }
        binding.pushGuideClose.setOnClickListener {
            result(false)
            dismiss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.apply {
            setCanceledOnTouchOutside(false)
            behavior.apply {
                maxWidth = (600 * resources.displayMetrics.density).toInt()
                // 小屏、横屏与大字号下限制弹层高度，长内容由原生滚动容器承载。
                maxHeight = (resources.displayMetrics.heightPixels * 0.9f).toInt()
                setShouldRemoveExpandedCorners(false)
                isDraggable = false
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            window?.let { window ->
                window.setDimAmount(0.63f)
                androidx.core.view
                    .WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightNavigationBars = true
                // API 26 不支持深色导航图标，保留深色系统栏以保证返回键可见。
                if (android.os.Build.VERSION.SDK_INT < 27)
                    window.navigationBarColor = android.graphics.Color.BLACK
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        result(false)
        super.onCancel(dialog)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("result.sent", resultSent)
        super.onSaveInstanceState(outState)
    }

    private fun result(allow: Boolean) {
        if (resultSent) return
        resultSent = true
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putBoolean(ALLOW, allow) })
    }

    companion object {
        const val TAG = "push.permission.guide"
        const val RESULT = "push.permission.guide.result"
        const val ALLOW = "allow"
    }
}
