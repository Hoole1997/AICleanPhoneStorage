package com.example.aicleanphonestorage.feature.notifications.ui

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.data.OneShotTransfer
import com.example.aicleanphonestorage.core.ui.loading.LoadingUiState
import com.example.aicleanphonestorage.core.ui.loading.TaskLoadingDialogFragment
import com.example.aicleanphonestorage.feature.notifications.data.NotificationCatalog

/** 和Network Traffic共用Loading组件/时间轴，独立tag与result key隔离两个入口的取消事件。 */
internal class NotificationEntryCoordinator(
    private val activity: AppCompatActivity,
    private val viewModel: NotificationCleanerViewModel,
    private val transfer: OneShotTransfer<NotificationCatalog>,
) {
    init {
        activity.supportFragmentManager.setFragmentResultListener(CANCEL_RESULT, activity) { _, result -> viewModel.cancelLoading(result.getLong(TaskLoadingDialogFragment.REQUEST_ID)) }
        activity.supportFragmentManager.setFragmentResultListener(NotificationEntryDialogFragment.RESULT, activity) { _, result ->
            when (result.getString("action")) {
                "access" -> {
                    viewModel.awaitAccess()
                    if (!NotificationAccessSettings.open(activity)) {
                        Toast.makeText(activity, R.string.notification_settings_unavailable, Toast.LENGTH_SHORT).show()
                        viewModel.cancelEntry()
                    }
                }
                "retry" -> viewModel.onForeground().also { viewModel.refresh() }
                else -> viewModel.cancelEntry()
            }
        }
    }
    fun render(state: NotificationUiState) {
        val manager = activity.supportFragmentManager
        if (manager.isStateSaved || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val loading = when (val phase = state.phase) {
            NotificationPhase.CheckingAccess -> LoadingUiState(0, activity.getString(R.string.notification_loading), activity.getString(R.string.notification_loading_access), resultKey = CANCEL_RESULT)
            is NotificationPhase.Loading -> {
                val progress = phase.frame?.detail
                LoadingUiState(phase.id, activity.getString(R.string.notification_loading),
                    if ((progress?.total ?: 0) > 0) activity.getString(R.string.notification_loading_apps, progress?.completed ?: 0, progress?.total ?: 0)
                    else activity.getString(R.string.notification_loading), phase.frame?.percent, resultKey = CANCEL_RESULT)
            }
            else -> null
        }
        val dialog = manager.findFragmentByTag(LOADING_TAG) as? TaskLoadingDialogFragment
        if (loading == null) dialog?.dismiss()
        else if (dialog != null) dialog.render(loading)
        else TaskLoadingDialogFragment.newInstance(loading).showNow(manager, LOADING_TAG)
        val needsMessage = state.phase == NotificationPhase.NeedsAccess || state.phase == NotificationPhase.Failed
        val message = manager.findFragmentByTag(NotificationEntryDialogFragment.TAG) as? NotificationEntryDialogFragment
        if (!needsMessage) message?.dismiss()
        else if (message == null) NotificationEntryDialogFragment.create(state.phase == NotificationPhase.NeedsAccess).showNow(manager, NotificationEntryDialogFragment.TAG)
        if (state.phase == NotificationPhase.Ready) {
            val catalog = viewModel.consumeCatalog() ?: return
            activity.startActivity(Intent(activity, NotificationCleanerActivity::class.java)
                .putExtra(NotificationCleanerActivity.EXTRA_CATALOG, transfer.put(catalog)))
        }
    }
    companion object {
        const val LOADING_TAG = "notification_entry.loading"
        const val CANCEL_RESULT = "notification_entry.loading.cancel"
    }
}
