package com.example.aicleanphonestorage.feature.filecleaner.ui

import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.permissions.*
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.*

/** 所有授权与扫描都在首页完成；系统设置和目录选择由 Activity Result 管理生命周期。 */
internal class CleanupEntryCoordinator(
    private val activity: AppCompatActivity,
    private val model: CleanupEntryViewModel,
    private val permissions: PermissionCoordinator,
) {
    private val ads = InterstitialActions(activity)

    init {
        CleanupFeature.entries.forEach { feature ->
            ads.register(InterstitialPlacements.scan(feature)) { token -> openResult(feature, token) }
        }
        permissions.register(
            ROUTE,
            before = {
                (model.state.value as? CleanupEntryState.Permission)?.let {
                    model.awaitPermission(it.feature)
                }
            },
        ) { result ->
            val feature = (model.state.value as? CleanupEntryState.Awaiting)?.feature
            when {
                result.granted && result.directory != null && feature != null ->
                    model.rememberTree(result.directory, feature)
                result.granted -> model.onForeground()
                else -> model.cancel()
            }
        }
        activity.supportFragmentManager.setFragmentResultListener(CANCEL, activity) { _, result ->
            model.cancel(result.getLong(TaskLoadingDialogFragment.REQUEST_ID))
        }
        activity.supportFragmentManager.setFragmentResultListener(
            CleanupMessageDialog.RESULT,
            activity,
        ) { _, result ->
            val failed = model.state.value as? CleanupEntryState.Failed
            if (result.getString("action") == "positive" && failed != null)
                model.begin(failed.feature)
            else model.cancel()
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
                    bytes = if (state.feature == com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature.SMART_CLEAN)
                        detail.bytes ?: 0L else null,
                )
            if (existing == null)
                TaskLoadingDialogFragment.newInstance(loading).showNow(manager, LOADING)
            else existing.render(loading)
        } else existing?.dismiss()
        val kind =
            (state as? CleanupEntryState.Permission)?.request?.let {
                when (it) {
                    AccessRequest.VIDEOS -> PermissionKind.VIDEOS
                    AccessRequest.PHOTOS -> PermissionKind.PHOTOS
                    AccessRequest.ALL_FILES -> PermissionKind.ALL_FILES
                    AccessRequest.DIRECTORY -> PermissionKind.DIRECTORY
                    AccessRequest.NONE -> null
                }
            }
        val purpose = when ((state as? CleanupEntryState.Permission)?.feature) {
            com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature.VIDEOS -> PermissionPurpose.VIDEO_CLEANER
            com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature.SIMILAR_PHOTOS -> PermissionPurpose.SIMILAR_PHOTOS
            else -> PermissionPurpose.DEFAULT
        }
        permissions.rationale(ROUTE, kind, purpose)
        val dialog = manager.findFragmentByTag(CleanupMessageDialog.TAG) as? CleanupMessageDialog
        if (state !is CleanupEntryState.Failed) dialog?.dismiss()
        else if (dialog == null)
            CleanupMessageDialog.create(
                    "entry",
                    activity.getString(R.string.cleanup_scan_failed),
                    activity.getString(R.string.cleanup_scan_failed),
                    activity.getString(R.string.traffic_retry),
                    activity.getString(R.string.cleanup_cancel),
                )
                .showNow(manager, CleanupMessageDialog.TAG)
        if (state is CleanupEntryState.Ready) {
            if (ads.busy) return
            // 消费成功结果后仅保留索引 ID；SDK 关闭/失败后由当前 Activity 续接导航。
            val handle = model.consume() ?: return
            val slot = InterstitialPlacements.scan(handle.feature)
            ads.run(slot, slot, handle.id)
        }
    }

    private fun openResult(feature: CleanupFeature, token: Long) {
        activity.startActivity(
            Intent(activity, if (feature == CleanupFeature.SMART_CLEAN)
                com.example.aicleanphonestorage.feature.junkcleaner.ui.JunkCleaningActivity::class.java
                else FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, token)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, feature.name)
        )
    }

    private fun toast() =
        Toast.makeText(activity, R.string.cleanup_scan_failed, Toast.LENGTH_SHORT).show()

    companion object {
        private const val ROUTE = "permission.files"
        private const val LOADING = "cleanup.entry.loading"
        private const val CANCEL = "cleanup.entry.cancel"
    }
}
