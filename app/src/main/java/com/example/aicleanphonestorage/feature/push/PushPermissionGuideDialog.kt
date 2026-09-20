package com.example.aicleanphonestorage.feature.push

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.permissions.PermissionSheetUi
import com.example.aicleanphonestorage.databinding.DialogAppPermissionBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** 只展示授权说明并上报选择；系统权限操作由首页协调器执行。 */
class PushPermissionGuideDialog : BottomSheetDialogFragment() {
    private var resultSent = false

    override fun getTheme() = R.style.ThemeOverlay_Clean_PermissionSheet

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        resultSent = state?.getBoolean("result.sent") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val binding = DialogAppPermissionBinding.inflate(inflater, container, false)
        binding.permissionTitle.setText(R.string.push_guide_title)
        binding.permissionMessage.setText(R.string.push_guide_message)
        binding.permissionIcon.setImageResource(R.drawable.push_permission_bell)
        binding.permissionContinue.setText(R.string.push_guide_allow)
        binding.permissionCancel.setText(R.string.permission_not_now)
        PermissionSheetUi.prepare(binding)
        binding.permissionContinue.setOnClickListener {
            result(true)
            dismiss()
        }
        binding.permissionCancel.setOnClickListener {
            result(false)
            dismiss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let(PermissionSheetUi::show)
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
