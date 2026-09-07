package com.example.aicleanphonestorage.feature.networktraffic.ui

import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader

import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenNetworkTrafficBinding
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficApp
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficPeriod
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficSnapshot
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope

/** 常驻列表+字段级Diff；加载不清空、不整体变暗、不重新设置adapter或滚动位置。 */
internal class NetworkTrafficRenderer(
    private val binding: ScreenNetworkTrafficBinding,
    scope: CoroutineScope,
    icons: AppIconLoader,
    onPeriod: (TrafficPeriod) -> Unit,
    onManage: (TrafficApp) -> Unit,
    private val onStatusAction: () -> Unit,
) {
    private val adapter = TrafficListAdapter(scope, icons, onPeriod, onManage)
    private val refresh = TrafficRefreshProgress(binding.trafficRefreshIndicator, scope)
    private var lastSnapshot: TrafficSnapshot? = null
    private var lastPeriod: TrafficPeriod? = null
    private var committedSnapshot: TrafficSnapshot? = null
    private var committedPeriod: TrafficPeriod? = null
    private var latestState: TrafficUiState? = null
    private var lastError: TrafficStatus.Failed? = null

    init {
        binding.trafficList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.trafficList.adapter = adapter
        binding.trafficList.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false // Payload数值更新不播放整行交叉淡入，保留移动/增删动画。
            moveDuration = 180; addDuration = 120; removeDuration = 120
        }
        binding.trafficStatusAction.setOnClickListener { onStatusAction() }
    }

    fun render(state: TrafficUiState) = with(binding) {
        latestState = state
        val status = state.status
        when (status) {
            is TrafficStatus.Loading -> refresh.start(status.requestId)
            TrafficStatus.CheckingAccess -> refresh.start(0)
            TrafficStatus.Ready -> Unit // 数据真正提交完成后才开始进度条收尾。
            else -> refresh.abort()
        }
        val hasSnapshot = state.snapshot != null
        val showMessage = status == TrafficStatus.NeedsAccess || (!hasSnapshot && (status == TrafficStatus.Paused || status is TrafficStatus.Failed))
        trafficStatusPanel.isVisible = showMessage
        trafficList.isVisible = hasSnapshot && status != TrafficStatus.NeedsAccess
        trafficStatusTitle.setText(if (status == TrafficStatus.NeedsAccess) R.string.traffic_access_title else R.string.traffic_title)
        val errorText = when {
            status == TrafficStatus.NeedsAccess -> R.string.traffic_access_message
            status == TrafficStatus.Paused -> R.string.traffic_paused
            status is TrafficStatus.Failed && status.timeout -> R.string.traffic_timeout
            else -> R.string.traffic_failed
        }
        trafficStatusMessage.setText(errorText)
        trafficStatusAction.setText(if (status == TrafficStatus.NeedsAccess) R.string.traffic_access_action else R.string.traffic_retry)
        if (hasSnapshot && status is TrafficStatus.Failed && lastError != status) {
            Snackbar.make(root, errorText, Snackbar.LENGTH_LONG).setAction(R.string.traffic_retry) { onStatusAction() }.show()
        }
        lastError = status as? TrafficStatus.Failed
        state.snapshot?.let { snapshot ->
            val selected = if (status is TrafficStatus.Failed) snapshot.period else state.period
            if (snapshot !== lastSnapshot || selected != lastPeriod) {
                lastSnapshot = snapshot; lastPeriod = selected
                adapter.submit(snapshot, selected) {
                    committedSnapshot = snapshot; committedPeriod = selected
                    finishRefreshIfCommitted()
                }
            } else finishRefreshIfCommitted()
        }
    }

    private fun finishRefreshIfCommitted() {
        val current = latestState ?: return
        if (current.status == TrafficStatus.Ready && current.snapshot === committedSnapshot && current.period == committedPeriod) refresh.complete()
    }
    fun setActive(active: Boolean) { adapter.setActive(active); refresh.setActive(active) }
    fun dispose() { refresh.abort(); adapter.setActive(false); binding.trafficList.adapter = null }
}
