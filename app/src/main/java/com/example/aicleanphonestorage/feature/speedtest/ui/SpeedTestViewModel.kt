package com.example.aicleanphonestorage.feature.speedtest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMetrics
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetwork
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestProgress
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestRepository
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 只保存轻量测速数值与网络概况，不持有 View/Context；状态单向流向页面。 */
sealed interface SpeedTestUiState {
    val network: SpeedTestNetwork?

    data class Testing(val progress: SpeedTestProgress, override val network: SpeedTestNetwork?) : SpeedTestUiState
    /**
     * 测速成功。[revealed] 为 false 时等待 `scan_complete_speedtest` 插屏回调，页面继续显示 100% 测速画面，
     * 不提前露出结果；回调（含关闭、无填充、失败）后才展示结果页。
     */
    data class Done(
        val result: SpeedTestResult,
        override val network: SpeedTestNetwork?,
        val revealed: Boolean = true,
    ) : SpeedTestUiState
    /** [previous] 为本页上一次成功结果；取消失败弹框时回到它，没有则关闭页面。 */
    data class Failed(
        val reason: SpeedTestException.Reason,
        val previous: Done?,
        override val network: SpeedTestNetwork?,
    ) : SpeedTestUiState
}

/**
 * 页面进入前台才开始测速；测速中离开前台立即取消全部连接（不在后台消耗流量与电量），
 * 回到前台重新完整测一次，不拼接中断前后的数据。
 */
class SpeedTestViewModel(
    private val repository: SpeedTestRepository,
    private val readNetwork: suspend () -> SpeedTestNetwork?,
) : ViewModel() {
    private val current = MutableStateFlow<SpeedTestUiState>(SpeedTestUiState.Testing(START, null))
    val state: StateFlow<SpeedTestUiState> = current.asStateFlow()
    private var job: Job? = null
    private var pendingStart = true
    private var lastDone: SpeedTestUiState.Done? = null

    /** 测速代次：广告回调携带代次，旧回调或重复回调不能揭示后一次测速结果。 */
    var generation: Long = 0
        private set

    fun onForeground() {
        if (pendingStart) start()
    }

    fun onBackground() {
        if (current.value !is SpeedTestUiState.Testing) return
        job?.cancel()
        job = null
        pendingStart = true
        current.value = SpeedTestUiState.Testing(START, current.value.network)
    }

    /** Test Again 与失败弹框 Retry。 */
    fun start() {
        pendingStart = false
        job?.cancel()
        generation++
        job = viewModelScope.launch {
            current.value = SpeedTestUiState.Testing(START, current.value.network)
            val network = try { readNetwork() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { null }
            current.value = SpeedTestUiState.Testing(START, network)
            try {
                var completed = false
                repository.run().collect { event ->
                    current.value = when (event) {
                        is SpeedTestEvent.Progress -> SpeedTestUiState.Testing(event.progress, network)
                        is SpeedTestEvent.Completed -> {
                            completed = true
                            SpeedTestUiState.Done(event.result, network, revealed = false)
                        }
                    }
                }
                if (!completed) throw SpeedTestException(SpeedTestException.Reason.Failed)
            } catch (error: SpeedTestException) {
                current.value = SpeedTestUiState.Failed(error.reason, lastDone, network)
            }
        }
    }

    /** 扫描完成插屏回调后展示结果；只接受当前代次且仍在等待的结果。 */
    fun revealResult(expectedGeneration: Long) {
        val done = current.value as? SpeedTestUiState.Done ?: return
        if (expectedGeneration != generation || done.revealed) return
        current.value = done.copy(revealed = true).also { lastDone = it }
    }

    /** 失败弹框 Cancel：有上一次成功结果则恢复展示并返回 false；否则返回 true 由页面关闭。 */
    fun dismissFailure(): Boolean {
        val failed = current.value as? SpeedTestUiState.Failed ?: return false
        val previous = failed.previous ?: return true
        current.value = previous
        return false
    }

    private companion object {
        val START = SpeedTestProgress(SpeedTestPhase.Connecting, 0f, SpeedTestMetrics(), null)
    }
}
