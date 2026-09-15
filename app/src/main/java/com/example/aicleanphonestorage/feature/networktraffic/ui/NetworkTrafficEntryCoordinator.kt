package com.example.aicleanphonestorage.feature.networktraffic.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.permissions.*
import com.example.aicleanphonestorage.core.ui.loading.LoadingUiState
import com.example.aicleanphonestorage.core.ui.loading.TaskLoadingDialogFragment
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficSnapshotTransfer
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficStage
import com.example.aicleanphonestorage.feature.networktraffic.data.UsageAvailability

/** 首页持有的 UI 协调器：先授权/加载，成功后只传一次摘要令牌；不把 Activity 存入 ViewModel。 */
internal class NetworkTrafficEntryCoordinator(
    private val activity: AppCompatActivity,
    private val viewModel: NetworkTrafficViewModel,
    private val transfer: TrafficSnapshotTransfer,
    private val permissions: PermissionCoordinator,
) {
    init {
        permissions.register(
            ROUTE,
            before = { kind ->
                if (kind == PermissionKind.PHONE) viewModel.markPhonePromptHandled()
                viewModel.awaitPermission()
            },
        ) { result ->
            when {
                result.cancelled || result.unavailable -> viewModel.cancelEntry()
                result.kind == PermissionKind.PHONE -> {
                    viewModel.markPhonePromptHandled()
                    viewModel.onForeground()
                    render(viewModel.state.value)
                }
                result.granted -> viewModel.onForeground()
                else -> viewModel.cancelEntry()
            }
        }
        activity.supportFragmentManager.setFragmentResultListener(
            TaskLoadingDialogFragment.RESULT_KEY,
            activity,
        ) { _, result ->
            if (viewModel.cancelByUser(result.getLong(TaskLoadingDialogFragment.REQUEST_ID)))
                viewModel.cancelEntry()
        }
        activity.supportFragmentManager.setFragmentResultListener(
            TrafficEntryDialogFragment.RESULT,
            activity,
        ) { _, result ->
            if (result.getString("action") == TrafficEntryDialogFragment.RETRY)
                viewModel.selectPeriod(viewModel.state.value.period)
            else viewModel.cancelEntry()
        }
    }

    fun render(state: TrafficUiState) {
        val manager = activity.supportFragmentManager
        if (
            manager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val loading =
            when (val status = state.status) {
                TrafficStatus.CheckingAccess ->
                    LoadingUiState(
                        0,
                        activity.getString(R.string.traffic_loading),
                        activity.getString(R.string.traffic_checking_access),
                    )
                is TrafficStatus.Loading ->
                    LoadingUiState(
                        status.requestId,
                        activity.getString(R.string.traffic_loading),
                        when (status.progress.stage) {
                            TrafficStage.MOBILE ->
                                activity.getString(R.string.traffic_reading_mobile)
                            TrafficStage.WIFI -> activity.getString(R.string.traffic_reading_wifi)
                            TrafficStage.APPLICATIONS ->
                                if (status.progress.total == null || status.progress.total == 0)
                                    activity.getString(R.string.traffic_preparing_results)
                                else
                                    activity.getString(
                                        R.string.traffic_reading_apps,
                                        status.progress.completed ?: 0,
                                        status.progress.total ?: 0,
                                    )
                        },
                        status.displayPercent,
                    )
                else -> null
            }
        val existingLoading =
            manager.findFragmentByTag(TaskLoadingDialogFragment.TAG) as? TaskLoadingDialogFragment
        if (loading == null) existingLoading?.dismiss()
        else if (existingLoading != null) existingLoading.render(loading)
        else
            TaskLoadingDialogFragment.newInstance(loading)
                .showNow(manager, TaskLoadingDialogFragment.TAG)

        val permissionKind =
            when {
                state.status == TrafficStatus.NeedsAccess -> PermissionKind.USAGE
                state.status == TrafficStatus.Ready &&
                    state.snapshot?.mobile?.availability ==
                        UsageAvailability.PHONE_PERMISSION_REQUIRED &&
                    !viewModel.phonePromptHandled -> PermissionKind.PHONE
                else -> null
            }
        permissions.rationale(ROUTE, permissionKind)
        val failed = state.status is TrafficStatus.Failed || state.status == TrafficStatus.Paused
        val message =
            manager.findFragmentByTag(TrafficEntryDialogFragment.TAG) as? TrafficEntryDialogFragment
        if (!failed) message?.dismiss()
        else if (message == null)
            TrafficEntryDialogFragment.create(TrafficEntryDialogFragment.RETRY)
                .showNow(manager, TrafficEntryDialogFragment.TAG)

        if (state.status == TrafficStatus.Ready && permissionKind == null) {
            val snapshot = viewModel.consumeEntrySnapshot() ?: return
            val token = transfer.put(snapshot)
            activity.startActivity(
                Intent(activity, NetworkTrafficActivity::class.java)
                    .putExtra(NetworkTrafficActivity.EXTRA_SNAPSHOT_TOKEN, token)
            )
        }
    }

    companion object {
        private const val ROUTE = "permission.traffic"
    }
}
