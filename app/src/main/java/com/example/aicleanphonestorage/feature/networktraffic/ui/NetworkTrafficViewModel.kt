package com.example.aicleanphonestorage.feature.networktraffic.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.networktraffic.data.NetworkTrafficRepository
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficPeriod
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficProgress
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficSnapshot
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficStage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryLoader
import com.example.aicleanphonestorage.core.ui.loading.TaskProgress

sealed interface TrafficStatus {
    data object Idle : TrafficStatus
    data object AwaitingPermission : TrafficStatus
    data object CheckingAccess : TrafficStatus
    data object NeedsAccess : TrafficStatus
    data class Loading(val requestId: Long, val progress: TrafficProgress, val displayPercent: Int? = progress.percent) : TrafficStatus
    data object Ready : TrafficStatus
    data object Paused : TrafficStatus
    data class Failed(val timeout: Boolean = false) : TrafficStatus
}

data class TrafficUiState(
    val period: TrafficPeriod,
    val status: TrafficStatus = TrafficStatus.CheckingAccess,
    val snapshot: TrafficSnapshot? = null,
)

/** UI 事件归主线程，数据进度以原子 StateFlow 更新。任务 ID 防止取消/快速筛选后的旧结果覆盖新状态。 */
class NetworkTrafficViewModel(
    private val repository: NetworkTrafficRepository,
    private val savedState: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
    private val entryMode: Boolean = false,
    initialSnapshot: TrafficSnapshot? = null,
    private val minimumEntryLoadingMillis: () -> Long = { TimedEntryLoader.defaultDurationMillis() },
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val initialPeriod = TrafficPeriod.fromSavedValue(savedState[PERIOD_KEY])
    private val mutableState = MutableStateFlow(initialSnapshot?.let { TrafficUiState(it.period, TrafficStatus.Ready, it) }
        ?: TrafficUiState(initialPeriod, if (entryMode && savedState.get<Boolean>(ENTRY_PENDING) != true) TrafficStatus.Idle else TrafficStatus.CheckingAccess))
    val state = mutableState.asStateFlow()
    private var sequence = 0L
    private var query: Job? = null
    private var accessCheck: Job? = null
    private var timeout: Job? = null

    fun onForeground() {
        if (entryMode && state.value.status == TrafficStatus.Idle) return
        accessCheck?.cancel()
        accessCheck = viewModelScope.launch {
            try {
                val granted = repository.hasUsageAccess()
                currentCoroutineContext().ensureActive()
                if (!granted) {
                    stopQuery()
                    mutableState.update { it.copy(status = TrafficStatus.NeedsAccess, snapshot = null) }
                } else {
                    val current = state.value
                    val shouldLoad = current.status == TrafficStatus.CheckingAccess || current.status == TrafficStatus.NeedsAccess || current.status == TrafficStatus.AwaitingPermission ||
                        (current.status == TrafficStatus.Ready && current.snapshot?.let { now() - it.window.endMillis !in 0..30_000 } == true)
                    if (shouldLoad) selectPeriod(current.period)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                mutableState.update { it.copy(status = TrafficStatus.NeedsAccess, snapshot = null) }
            }
        }
    }

    fun beginEntry() {
        if (!entryMode || state.value.status != TrafficStatus.Idle) return
        savedState[ENTRY_PENDING] = true
        savedState[PHONE_HANDLED] = false
        mutableState.value = TrafficUiState(TrafficPeriod.THIS_MONTH)
        onForeground()
    }

    fun awaitPermission() {
        accessCheck?.cancel()
        stopQuery()
        mutableState.update { it.copy(status = TrafficStatus.AwaitingPermission) }
    }

    val phonePromptHandled: Boolean get() = savedState[PHONE_HANDLED] ?: false
    fun markPhonePromptHandled() { savedState[PHONE_HANDLED] = true }

    /** 只有数据就绪才允许导航，并在导航前消费状态，旋转/返回首页不会二次打开结果页。 */
    fun consumeEntrySnapshot(): TrafficSnapshot? {
        if (!entryMode || state.value.status != TrafficStatus.Ready) return null
        val result = state.value.snapshot ?: return null
        cancelEntry()
        return result
    }

    fun cancelEntry() {
        accessCheck?.cancel()
        stopQuery()
        savedState[ENTRY_PENDING] = false
        mutableState.value = TrafficUiState(TrafficPeriod.THIS_MONTH, TrafficStatus.Idle)
    }

    fun selectPeriod(period: TrafficPeriod) {
        // 快速重复点击相同筛选不重启；不同筛选则取消旧请求并只保留最新的等待者。
        if (state.value.status is TrafficStatus.Loading && state.value.period == period) return
        stopQuery()
        val id = ++sequence
        // 仅首页入口提供广告展示窗口：每次任务随机一次，不延迟页面内日期刷新。
        val minimumDisplay = if (entryMode) minimumEntryLoadingMillis().coerceIn(0, 10_000) else 0L
        savedState[PERIOD_KEY] = period.name
        mutableState.update { it.copy(period = period, status = TrafficStatus.Loading(id, TrafficProgress(TrafficStage.MOBILE))) }
        val deadline = viewModelScope.launch {
            delay(15_000)
            if ((state.value.status as? TrafficStatus.Loading)?.requestId == id) {
                query?.cancel()
                mutableState.update { it.copy(status = TrafficStatus.Failed(timeout = true)) }
            }
        }
        timeout = deadline
        query = viewModelScope.launch {
            try {
                val result = if (entryMode) {
                    TimedEntryLoader({ minimumDisplay }, monotonicMillis).load(
                        initialStage = "MOBILE", count = { it: TrafficSnapshot -> it.apps.size },
                        onStalled = { failIfCurrent(id) },
                        onFrame = { frame -> publishProgress(id, TrafficProgress(TrafficStage.valueOf(frame.detail.stage), frame.detail.completed, frame.detail.total), frame.percent) },
                    ) { report -> repository.load(period) { report(TaskProgress(it.stage.name, it.completed, it.total)) } }
                } else repository.load(period) { publishProgress(id, it, it.percent) }
                mutableState.update { current ->
                    if ((current.status as? TrafficStatus.Loading)?.requestId == id)
                        TrafficUiState(period, TrafficStatus.Ready, result) else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                if ((state.value.status as? TrafficStatus.Loading)?.requestId == id)
                    mutableState.update { it.copy(status = TrafficStatus.NeedsAccess, snapshot = null) }
            } catch (error: IOException) {
                failIfCurrent(id)
            } finally {
                failIfCurrent(id)
                deadline.cancel()
            }
        }
    }

    private fun publishProgress(id: Long, progress: TrafficProgress, percent: Int?) {
        mutableState.update { current ->
            if ((current.status as? TrafficStatus.Loading)?.requestId == id)
                current.copy(status = TrafficStatus.Loading(id, progress, percent)) else current
        }
    }

    private fun failIfCurrent(id: Long) {
        mutableState.update { if ((it.status as? TrafficStatus.Loading)?.requestId == id) it.copy(status = TrafficStatus.Failed()) else it }
    }

    /** 返回 true 表示首屏任务被取消，应退出模块。旧弹窗不能取消新任务。 */
    fun cancelByUser(requestId: Long): Boolean {
        val current = state.value
        val currentId = (current.status as? TrafficStatus.Loading)?.requestId ?: if (current.status == TrafficStatus.CheckingAccess) 0L else -1L
        if (currentId != requestId) return false
        if (entryMode) { cancelEntry(); return true }
        accessCheck?.cancel()
        stopQuery()
        restorePreviousOrPause()
        return current.snapshot == null
    }

    fun onBackground() {
        accessCheck?.cancel()
        if (state.value.status is TrafficStatus.Loading || state.value.status == TrafficStatus.CheckingAccess) {
            if (entryMode) { cancelEntry(); return }
            stopQuery()
            restorePreviousOrPause()
        }
    }

    private fun restorePreviousOrPause() {
        mutableState.update { current -> current.snapshot?.let { TrafficUiState(it.period, TrafficStatus.Ready, it) }
            ?: current.copy(status = TrafficStatus.Paused) }
        savedState[PERIOD_KEY] = state.value.period.name
    }

    private fun stopQuery() { query?.cancel(); timeout?.cancel() }

    companion object {
        private const val PERIOD_KEY = "network_traffic.period"
        private const val ENTRY_PENDING = "network_traffic.entry_pending"
        private const val PHONE_HANDLED = "network_traffic.phone_handled"
    }
}
