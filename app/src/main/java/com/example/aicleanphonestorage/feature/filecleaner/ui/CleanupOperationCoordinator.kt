package com.example.aicleanphonestorage.feature.filecleaner.ui

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
import com.example.aicleanphonestorage.app.ad.HomeExitAdContract
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.ui.completion.CompletionContract
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 所有文件清理页面共享确认、系统删除授权与完成结果；不在页面间复制操作流程。 */
internal class CleanupOperationCoordinator(
    private val activity: AppCompatActivity,
    private val model: CleanupViewModel,
    saved: Bundle?,
    private val ads: InterstitialActions,
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
                    // 父页进程重建时索引可能尚未恢复，仍保留完成页携回的来源功能。
                    val source = CleanupFeature.entries.firstOrNull {
                        it.name == result.data?.getStringExtra(CompletionContract.SOURCE)
                    } ?: model.state.value.handle?.feature
                    activity.startActivity(
                        HomeExitAdContract.intent(activity,
                            InterstitialPlacements.exit(source))
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
        // 请求中只保存确认时的操作编号；广告回调后 ViewModel 再验证编号，避免执行后来改变的选择。
        ads.register(CONFIRM_AD) { id -> model.confirm(id) }
        ads.register(COMPRESS_AD) { id -> model.startCompression(id) }
        ads.register(EMPTY_JUNK_AD) { id -> model.startEmptyJunk(id) }
        ads.register(ORIGINALS_AD) { id -> model.removeOriginals(id) }
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
            if (ads.busy) return@setFragmentResultListener
            val identity = result.getString("identity").orEmpty()
            val positive = result.getString("action") == "positive"
            val op = model.state.value.operation
            when {
                identity.startsWith("originals:") -> {
                    val id = originalsConfirmation
                    originalsConfirmation = null
                    if (positive && id != null) requestAction(ORIGINALS_AD, id)
                    else model.dismissOperation()
                }
                op is CleanupOperationState.Confirm && identity == "confirm:${op.id}" ->
                    if (positive) requestAction(CONFIRM_AD, op.id) else model.dismissOperation()
            }
        }
    }

    private fun requestAction(action: String, id: Long) {
        ads.run(action, InterstitialPlacements.clean(model.state.value.handle?.feature), payload = id)
    }

    fun render(operation: CleanupOperationState) {
        // 退出回调触发 finish 后，生命周期可能尚未降级；此时不能再打开确认框或完成页。
        if (
            ads.busy || activity.isFinishing || activity.isDestroyed ||
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
                        activity.getString(R.string.cleanup_delete_confirm),
                        activity.getString(R.string.cleanup_confirm),
                        activity.getString(R.string.cleanup_cancel),
                    )
                else -> null
            }
        if (old?.identity != message?.identity) {
            old?.dismissNow()
            message?.showNow(activity.supportFragmentManager, CleanupMessageDialog.TAG)
        }
        if (operation is CleanupOperationState.EmptyJunkReady) {
            requestAction(EMPTY_JUNK_AD, operation.id)
            return
        }
        if (operation is CleanupOperationState.CompressionReady) {
            // 点击 Compress 已表达创建副本的意图，直接进入既有广告/压缩流程，不再弹二次确认。
            requestAction(COMPRESS_AD, operation.id)
            return
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
                    source = feature.name,
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
        private const val EMPTY_JUNK_AD = "ad.junk.empty"
        private const val COMPRESS_AD = "ad.compress.requested"
        private const val CONFIRM_AD = "ad.clean.confirmed"
        private const val ORIGINALS_AD = "ad.originals.confirmed"
        private const val WORKING = "cleanup.operation.loading"
        private const val WORK_CANCEL = "cleanup.operation.cancel"
    }
}
