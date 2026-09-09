package com.example.aicleanphonestorage.core.permissions

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogAppPermissionBinding

/** App 风格的说明弹框，不模拟系统权限开关；实际授权仍进入 Android 标准界面。 */
class PermissionDialogFragment : DialogFragment() {
    val route: String
        get() = requireArguments().getString("route").orEmpty()

    private val kind: PermissionKind
        get() = PermissionKind.valueOf(requireArguments().getString("kind")!!)

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val binding = DialogAppPermissionBinding.inflate(inflater, container, false)
        val spec =
            when (kind) {
                PermissionKind.POST_NOTIFICATIONS ->
                    Triple(R.string.push_permission_title, R.string.push_permission_message,
                        R.drawable.ic_tool_notifications)
                PermissionKind.USAGE ->
                    Triple(
                        R.string.permission_usage_title,
                        R.string.permission_usage_message,
                        R.drawable.ic_tool_network,
                    )
                PermissionKind.NOTIFICATIONS ->
                    Triple(
                        R.string.permission_notification_title,
                        R.string.permission_notification_message,
                        R.drawable.ic_tool_notifications,
                    )
                PermissionKind.ALL_FILES ->
                    Triple(
                        R.string.permission_files_title,
                        R.string.permission_files_message,
                        R.drawable.ic_tool_large_files,
                    )
                PermissionKind.PHOTOS ->
                    Triple(
                        R.string.permission_photos_title,
                        R.string.permission_photos_message,
                        R.drawable.ic_tool_compress,
                    )
                PermissionKind.PHONE ->
                    Triple(
                        R.string.permission_phone_title,
                        R.string.permission_phone_message,
                        R.drawable.ic_tool_network,
                    )
                PermissionKind.DIRECTORY ->
                    Triple(
                        R.string.permission_folder_title,
                        R.string.permission_folder_message,
                        R.drawable.junk_file,
                    )
            }
        binding.permissionTitle.setText(spec.first)
        binding.permissionMessage.setText(spec.second)
        binding.permissionIcon.setImageResource(spec.third)
        val settings = requireArguments().getBoolean("settings")
        binding.permissionContinue.setText(
            if (kind == PermissionKind.DIRECTORY) R.string.cleanup_choose_folder
            else if (kind.special || settings) R.string.permission_open_settings
            else R.string.permission_continue
        )
        binding.permissionCancel.setText(
            if (kind == PermissionKind.PHONE) R.string.traffic_wifi_only
            else R.string.permission_not_now
        )
        // 文件访问说明只引导当前权限申请，不混入目录选择这一独立授权流程。
        binding.permissionContinue.setOnClickListener {
            result(if (settings) "settings" else "continue")
            dismiss()
        }
        binding.permissionCancel.setOnClickListener {
            result(if (kind == PermissionKind.PHONE) "skip" else "cancel")
            dismiss()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width =
            minOf(
                (315 * metrics.density).toInt(),
                metrics.widthPixels - (48 * metrics.density).toInt(),
            )
        val maxHeight = metrics.heightPixels - (80 * metrics.density).toInt()
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

    override fun onCancel(dialog: DialogInterface) {
        result("cancel")
        super.onCancel(dialog)
    }

    private fun result(action: String) =
        parentFragmentManager.setFragmentResult(
            RESULT,
            Bundle().apply {
                putString("route", route)
                putString("kind", kind.name)
                putString("action", action)
            },
        )

    companion object {
        const val TAG = "permission.rationale"
        const val RESULT = "permission.rationale.result"

        internal fun create(route: String, kind: PermissionKind, settings: Boolean) =
            PermissionDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString("route", route)
                        putString("kind", kind.name)
                        putBoolean("settings", settings)
                    }
            }
    }
}
