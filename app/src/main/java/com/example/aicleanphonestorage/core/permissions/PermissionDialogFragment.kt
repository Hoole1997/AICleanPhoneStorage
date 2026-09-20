package com.example.aicleanphonestorage.core.permissions

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogAppPermissionBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** 功能说明与系统权限类型分开：同一种所有文件权限可用于不同的用户操作。 */
internal enum class PermissionPurpose {
    DEFAULT,
    VIDEO_CLEANER,
    SIMILAR_PHOTOS,
}

/** App 风格的说明弹框，不模拟系统权限开关；实际授权仍进入 Android 标准界面。 */
class PermissionDialogFragment : BottomSheetDialogFragment() {
    override fun getTheme() = R.style.ThemeOverlay_Clean_PermissionSheet

    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultSent = savedInstanceState?.getBoolean("result.sent") ?: false
    }

    val route: String
        get() = requireArguments().getString("route").orEmpty()

    private val kind: PermissionKind
        get() = PermissionKind.valueOf(requireArguments().getString("kind")!!)

    private val purpose: PermissionPurpose
        get() =
            PermissionPurpose.entries.firstOrNull { it.name == arguments?.getString("purpose") }
                ?: PermissionPurpose.DEFAULT

    internal fun matches(route: String, kind: PermissionKind, purpose: PermissionPurpose) =
        this.route == route && this.kind == kind && this.purpose == purpose

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val binding = DialogAppPermissionBinding.inflate(inflater, container, false)
        val spec =
            if (purpose == PermissionPurpose.VIDEO_CLEANER)
                Triple(
                    R.string.video_permission_title,
                    R.string.video_permission_message,
                    R.drawable.permission_sheet_video,
                )
            else if (purpose == PermissionPurpose.SIMILAR_PHOTOS)
                Triple(
                    R.string.similar_permission_title,
                    R.string.similar_permission_message,
                    R.drawable.permission_sheet_photos,
                )
            else
                when (kind) {
                    PermissionKind.POST_NOTIFICATIONS ->
                        Triple(
                            R.string.push_permission_title,
                            R.string.push_permission_message,
                            R.drawable.push_permission_bell,
                        )
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
                            R.drawable.push_permission_bell,
                        )
                    PermissionKind.ALL_FILES ->
                        Triple(
                            R.string.permission_files_title,
                            R.string.permission_files_message,
                            R.drawable.ic_tool_large_files,
                        )
                    PermissionKind.VIDEOS ->
                        Triple(
                            R.string.video_permission_title,
                            R.string.video_permission_message,
                            R.drawable.permission_sheet_video,
                        )
                    PermissionKind.PHOTOS ->
                        Triple(
                            R.string.permission_photos_title,
                            R.string.permission_photos_message,
                            R.drawable.permission_sheet_photos,
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
            if (purpose != PermissionPurpose.DEFAULT) R.string.permission_allow
            else if (kind == PermissionKind.DIRECTORY) R.string.cleanup_choose_folder
            else if (kind.special || settings) R.string.permission_open_settings
            else if (kind == PermissionKind.VIDEOS) R.string.video_permission_allow
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
        PermissionSheetUi.prepare(binding)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let(PermissionSheetUi::show)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("result.sent", resultSent)
        super.onSaveInstanceState(outState)
    }

    override fun onCancel(dialog: DialogInterface) {
        result("cancel")
        super.onCancel(dialog)
    }

    private fun result(action: String) {
        // 按钮/返回键与生命周期恢复共用单次结果，样式切换不重复启动授权页。
        if (resultSent) return
        resultSent = true
        parentFragmentManager.setFragmentResult(
            RESULT,
            Bundle().apply {
                putString("route", route)
                putString("kind", kind.name)
                putString("action", action)
            },
        )
    }

    companion object {
        const val TAG = "permission.rationale"
        const val RESULT = "permission.rationale.result"

        internal fun create(
            route: String,
            kind: PermissionKind,
            settings: Boolean,
            purpose: PermissionPurpose = PermissionPurpose.DEFAULT,
        ) =
            PermissionDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString("route", route)
                        putString("kind", kind.name)
                        putBoolean("settings", settings)
                        // 保存语义标识，旋转/进程重建后继续使用当前功能文案；回调仍携带实际权限 kind。
                        putString("purpose", purpose.name)
                    }
            }
    }
}
