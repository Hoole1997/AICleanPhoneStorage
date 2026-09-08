package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.*

/** 所有授权与扫描都在首页完成；系统设置和目录选择由 Activity Result 管理生命周期。 */
internal class CleanupEntryCoordinator(
    private val activity: AppCompatActivity,
    private val model: CleanupEntryViewModel,
    private val access: CleanupAccess,
) {
    private val photos =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            if (grants.values.any { it }) model.onForeground() else model.cancel()
        }
    private val settings =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.onForeground()
        }
    private val directory =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val feature = (model.state.value as? CleanupEntryState.Awaiting)?.feature
            if (uri == null || feature == null) model.cancel()
            else
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    model.rememberTree(uri.toString(), feature)
                } catch (_: SecurityException) {
                    model.cancel()
                    toast()
                }
        }

    init {
        activity.supportFragmentManager.setFragmentResultListener(CANCEL, activity) { _, _ ->
            model.cancel()
        }
        activity.supportFragmentManager.setFragmentResultListener(
            CleanupMessageDialog.RESULT,
            activity,
        ) { _, result ->
            val state = model.state.value
            val action = result.getString("action")
            when {
                action == "negative" -> model.cancel()
                state is CleanupEntryState.Failed -> model.begin(state.feature)
                state is CleanupEntryState.Permission -> {
                    model.awaitPermission(state.feature)
                    when {
                        action == "neutral" || state.request == AccessRequest.DIRECTORY ->
                            directory.launch(null)
                        state.request == AccessRequest.PHOTOS ->
                            photos.launch(access.photoPermissions())
                        state.request == AccessRequest.ALL_FILES && Build.VERSION.SDK_INT >= 30 -> {
                            try {
                                settings.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${activity.packageName}"),
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                directory.launch(null)
                            }
                        }
                        else -> model.cancel()
                    }
                }
            }
        }
    }

    fun render(state: CleanupEntryState) {
        val manager = activity.supportFragmentManager
        if (
            manager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val existing = manager.findFragmentByTag(LOADING) as? TaskLoadingDialogFragment
        if (state is CleanupEntryState.Loading) {
            val detail = state.frame.detail
            val message =
                if (detail.stage == "PHOTOS") activity.getString(R.string.junk_analyzing_photos)
                else if ((detail.total ?: 0) > 0)
                    activity.getString(
                        R.string.cleanup_prepare_files,
                        detail.completed ?: 0,
                        detail.total ?: 0,
                    )
                else activity.getString(R.string.cleanup_scanning_files)
            val loading =
                LoadingUiState(
                    state.id,
                    activity.getString(R.string.cleanup_scan),
                    message,
                    state.frame.percent ?: 0,
                    resultKey = CANCEL,
                )
            if (existing == null)
                TaskLoadingDialogFragment.newInstance(loading).showNow(manager, LOADING)
            else existing.render(loading)
        } else existing?.dismiss()
        val dialog = manager.findFragmentByTag(CleanupMessageDialog.TAG) as? CleanupMessageDialog
        if (state !is CleanupEntryState.Permission && state !is CleanupEntryState.Failed)
            dialog?.dismiss()
        else if (dialog == null) {
            val permission = state as? CleanupEntryState.Permission
            val message =
                when (permission?.request) {
                    AccessRequest.PHOTOS -> R.string.cleanup_photo_access
                    AccessRequest.ALL_FILES -> R.string.cleanup_all_access
                    AccessRequest.DIRECTORY -> R.string.cleanup_directory_access
                    else -> R.string.cleanup_scan_failed
                }
            CleanupMessageDialog.create(
                    "entry",
                    activity.getString(R.string.cleanup_access_title),
                    activity.getString(message),
                    activity.getString(
                        if (permission == null) R.string.traffic_retry else R.string.cleanup_allow
                    ),
                    activity.getString(R.string.cleanup_cancel),
                    if (permission?.request == AccessRequest.ALL_FILES)
                        activity.getString(R.string.cleanup_choose_folder)
                    else null,
                )
                .showNow(manager, CleanupMessageDialog.TAG)
        }
        if (state is CleanupEntryState.Ready) {
            val handle = model.consume() ?: return
            activity.startActivity(
                Intent(
                        activity,
                        if (
                            handle.feature ==
                                com.example.aicleanphonestorage.feature.filecleaner.data
                                    .CleanupFeature
                                    .SMART_CLEAN
                        )
                            com.example.aicleanphonestorage.feature.junkcleaner.ui
                                    .JunkCleaningActivity::class
                                .java
                        else FileCleanupActivity::class.java,
                    )
                    .putExtra(FileCleanupActivity.EXTRA_SCAN, handle.id)
            )
        }
    }

    private fun toast() =
        Toast.makeText(activity, R.string.cleanup_scan_failed, Toast.LENGTH_SHORT).show()

    companion object {
        private const val LOADING = "cleanup.entry.loading"
        private const val CANCEL = "cleanup.entry.cancel"
    }
}
