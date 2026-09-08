package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.loading.*

/** 所有文件清理页面共享确认、系统删除授权与完成结果；不在页面间复制操作流程。 */
internal class CleanupOperationCoordinator(
    private val activity: AppCompatActivity,
    private val model: CleanupViewModel,
    saved: Bundle?,
) {
    private var originalsConfirmation: Long? = saved?.getLong("originals")?.takeIf { it > 0 }
    private val consent =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            model.consentResult(it.resultCode == android.app.Activity.RESULT_OK)
        }

    init {
        activity.supportFragmentManager.setFragmentResultListener(WORK_CANCEL, activity) { _, _ ->
            model.onBackground()
        }
        activity.supportFragmentManager.setFragmentResultListener(
            CleanupMessageDialog.RESULT,
            activity,
        ) { _, result ->
            val identity = result.getString("identity").orEmpty()
            val positive = result.getString("action") == "positive"
            val op = model.state.value.operation
            when {
                identity.startsWith("originals:") -> {
                    val id = originalsConfirmation
                    originalsConfirmation = null
                    if (positive && id != null) model.removeOriginals(id)
                    else model.dismissOperation()
                }
                op is CleanupOperationState.Confirm ->
                    if (positive) model.confirm(op.id) else model.dismissOperation()
                op is CleanupOperationState.Result ->
                    if (positive && op.summary.originalsAvailable > 0) {
                        originalsConfirmation = op.id
                        render(model.state.value.operation)
                    } else model.dismissOperation()
            }
        }
    }

    fun render(operation: CleanupOperationState) {
        if (
            activity.supportFragmentManager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val loading =
            activity.supportFragmentManager.findFragmentByTag(WORKING) as? TaskLoadingDialogFragment
        if (operation is CleanupOperationState.Running) {
            val frame =
                LoadingUiState(
                    operation.id,
                    activity.getString(R.string.cleanup_working, operation.done, operation.total),
                    activity.getString(R.string.cleanup_processing_files),
                    if (operation.total > 0) operation.done * 100 / operation.total else null,
                    cancellable = true,
                    showAd = false,
                    resultKey = WORK_CANCEL,
                )
            if (loading == null)
                TaskLoadingDialogFragment.newInstance(frame)
                    .showNow(activity.supportFragmentManager, WORKING)
            else loading.render(frame)
        } else loading?.dismiss()
        val old =
            activity.supportFragmentManager.findFragmentByTag(CleanupMessageDialog.TAG)
                as? CleanupMessageDialog
        val message =
            when {
                originalsConfirmation != null ->
                    CleanupMessageDialog.create(
                        "originals:$originalsConfirmation",
                        activity.getString(R.string.cleanup_tips),
                        activity.getString(R.string.cleanup_originals_confirm),
                        activity.getString(R.string.cleanup_confirm),
                        activity.getString(R.string.cleanup_cancel),
                    )
                operation is CleanupOperationState.Confirm ->
                    CleanupMessageDialog.create(
                        "confirm:${operation.id}",
                        activity.getString(R.string.cleanup_tips),
                        if (operation.compress)
                            activity.getString(R.string.cleanup_compress_confirm, operation.count)
                        else
                            activity.getString(
                                R.string.cleanup_delete_confirm,
                                operation.count,
                                Formatter.formatShortFileSize(activity, operation.bytes),
                            ),
                        activity.getString(R.string.cleanup_confirm),
                        activity.getString(R.string.cleanup_cancel),
                    )
                operation is CleanupOperationState.Result ->
                    CleanupMessageDialog.create(
                        "result:${operation.id}",
                        activity.getString(R.string.cleanup_done),
                        activity.getString(
                            R.string.cleanup_result,
                            operation.summary.deleted,
                            operation.summary.copied,
                            operation.summary.skipped,
                            operation.summary.failed,
                        ),
                        activity.getString(
                            if (operation.summary.originalsAvailable > 0)
                                R.string.cleanup_remove_originals
                            else R.string.cleanup_done
                        ),
                        activity.getString(
                            if (operation.summary.originalsAvailable > 0)
                                R.string.cleanup_keep_originals
                            else R.string.cleanup_cancel
                        ),
                    )
                else -> null
            }
        if (old?.identity != message?.identity) {
            old?.dismissNow()
            message?.showNow(activity.supportFragmentManager, CleanupMessageDialog.TAG)
        }
        if (operation is CleanupOperationState.Consent && !model.waitingSystem) {
            model.consentLaunched()
            try {
                consent.launch(IntentSenderRequest.Builder(operation.sender).build())
            } catch (_: android.content.IntentSender.SendIntentException) {
                model.consentResult(false)
                toast(R.string.cleanup_action_failed)
            }
        }
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()

    fun save(out: Bundle) {
        originalsConfirmation?.let { out.putLong("originals", it) }
    }

    companion object {
        private const val WORKING = "cleanup.operation.loading"
        private const val WORK_CANCEL = "cleanup.operation.cancel"
    }
}
