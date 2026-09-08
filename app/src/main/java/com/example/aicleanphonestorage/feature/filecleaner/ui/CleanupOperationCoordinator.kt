package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.ui.completion.CompletionContract
import com.example.aicleanphonestorage.core.ui.loading.*

/** 所有文件清理页面共享确认、系统删除授权与完成结果；不在页面间复制操作流程。 */
internal class CleanupOperationCoordinator(
    private val activity: AppCompatActivity,
    private val model: CleanupViewModel,
    saved: Bundle?,
) {
    private var originalsConfirmation: Long? = saved?.getLong("originals")?.takeIf { it > 0 }
    // 保留到 Activity Result 返回，旋转/重建不会重复打开同一份完成页。
    private var presentedResult: Long? = saved?.getLong("completion.presented")?.takeIf { it > 0 }
    private val completion =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            val id = presentedResult ?: return@registerForActivityResult
            presentedResult = null
            val action =
                result.data
                    ?.takeIf { it.getLongExtra(CompletionContract.OPERATION, 0) == id }
                    ?.getStringExtra(CompletionContract.ACTION)
            if (action == CompletionContract.REMOVE_ORIGINALS) {
                originalsConfirmation = id
                render(model.state.value.operation)
            } else {
                model.dismissOperation()
                if (action == CompletionContract.CONTINUE) {
                    activity.startActivity(
                        Intent(activity, MainActivity::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                    )
                    activity.finish()
                }
            }
        }
    private val consent =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            model.consentResult(it.resultCode == android.app.Activity.RESULT_OK)
        }

    init {
        // Activity Result 在 STARTED 时分发；回到原图确认时没有新的业务状态发射，需在 RESUMED 补呈现。
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    render(model.state.value.operation)
                }
            }
        )
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
                else -> null
            }
        if (old?.identity != message?.identity) {
            old?.dismissNow()
            message?.showNow(activity.supportFragmentManager, CleanupMessageDialog.TAG)
        }
        if (
            operation is CleanupOperationState.Result &&
                originalsConfirmation == null &&
                presentedResult == null
        ) {
            val feature = model.state.value.handle?.feature ?: return
            presentedResult = operation.id
            completion.launch(
                CompletionContract.intent(
                    activity,
                    operation.summary.completionReport(feature, operation.id),
                )
            )
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
        presentedResult?.let { out.putLong("completion.presented", it) }
    }

    companion object {
        private const val WORKING = "cleanup.operation.loading"
        private const val WORK_CANCEL = "cleanup.operation.cancel"
    }
}
