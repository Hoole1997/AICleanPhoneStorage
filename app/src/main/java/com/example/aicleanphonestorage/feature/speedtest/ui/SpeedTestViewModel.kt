package com.example.aicleanphonestorage.feature.speedtest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestFailureAction
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestTelemetry
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    /** [previous] 仅用作弹框下方的上次完整结果；Cancel 按埋点文档返回首页。null 原因为未归类异常。 */
    data class Failed(
        val reason: SpeedTestException.Reason?,
        val previous: Done?,
        override val network: SpeedTestNetwork?,
    ) : SpeedTestUiState
}

/**
 * 页面进入前台才开始测速；测速中离开前台立即取消全部连接（不在后台消耗流量与电量），
 * 回到前台重新完整测一次，不拼接中断前后的数据。
 */
internal class SpeedTestViewModel(
    private val repository: SpeedTestRepository,
    private val telemetry: SpeedTestTelemetry = SpeedTestTelemetry(),
    private val readNetwork: suspend () -> SpeedTestNetwork?,
) : ViewModel() {
    private val current = MutableStateFlow<SpeedTestUiState>(SpeedTestUiState.Testing(START, null))
    val state: StateFlow<SpeedTestUiState> = current.asStateFlow()
    private var job: Job? = null
    private var pendingStart = true
    private var lastDone: SpeedTestUiState.Done? = null
    private var resultShownGeneration = -1L
    private var failureActionGeneration = -1L
    private var scanBackGeneration = -1L

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

    /** 每一轮完整测速独立计数；自动前台恢复与 Retry 不冒充 Test Again 点击。 */
    fun start() {
        pendingStart = false
        job?.cancel()
        generation++
        val startedGeneration = generation
        job = viewModelScope.launch {
            current.value = SpeedTestUiState.Testing(START, current.value.network)
            val network = try { readNetwork() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (startedGeneration != generation) return@launch
            current.value = SpeedTestUiState.Testing(START, network)
            try {
                var completed: SpeedTestResult? = null
                repository.run().collect { event ->
                    currentCoroutineContext().ensureActive()
                    if (startedGeneration == generation) when (event) {
                        is SpeedTestEvent.Progress -> current.value = SpeedTestUiState.Testing(event.progress, network)
                        is SpeedTestEvent.Completed -> completed = event.result
                    }
                }
                currentCoroutineContext().ensureActive()
                if (startedGeneration != generation) return@launch
                // 收集成功结束后才确认完整结果，末尾失败或取消不能先上报成功/请求插屏。
                val result = completed ?: throw SpeedTestException(SpeedTestException.Reason.ServerError)
                telemetry.scanResult()
                current.value = SpeedTestUiState.Done(result, network, revealed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpeedTestException) {
                currentCoroutineContext().ensureActive()
                if (startedGeneration == generation) current.value = SpeedTestUiState.Failed(error.reason, lastDone, network)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                // 未归类异常仍是失败；埋点保留 unknown，用户文案沿用既有默认无网络提示。
                if (startedGeneration == generation) current.value = SpeedTestUiState.Failed(null, lastDone, network)
            }
        }
    }

    /** 扫描完成插屏回调后展示结果；只接受当前代次且仍在等待的结果。 */
    fun revealResult(expectedGeneration: Long) {
        val done = current.value as? SpeedTestUiState.Done ?: return
        if (expectedGeneration != generation || done.revealed) return
        current.value = done.copy(revealed = true).also { lastDone = it }
    }

    /** renderer 在 RESUMED 且真正展示广告后的结果后调用；重渲染、重建与前后台切换不重复曝光。 */
    fun resultVisible() {
        val done = current.value as? SpeedTestUiState.Done ?: return
        if (!done.revealed || resultShownGeneration == generation) return
        resultShownGeneration = generation
        telemetry.resultShown()
    }

    fun testAgain() {
        val done = current.value as? SpeedTestUiState.Done ?: return
        if (!done.revealed) return
        telemetry.testAgain()
        start()
    }

    /** 顶部和系统返回共用退出协调器，在实际允许退出后调用；结果页与失败 Cancel 不算扫描返回。 */
    fun scanBackRequested() {
        val value = current.value
        if (value !is SpeedTestUiState.Testing && (value !is SpeedTestUiState.Done || value.revealed)) return
        if (scanBackGeneration == generation) return
        scanBackGeneration = generation
        // 已接受退出时立即停止连接，不能等 Activity.onStop 才让本轮终止。
        job?.cancel()
        job = null
        telemetry.scanBack()
    }

    /** FragmentResult 携带原弹框原因与代次，旧弹框/重复回调不能操作或上报下一轮失败。 */
    fun failureAction(
        expectedGeneration: Long,
        expectedReason: SpeedTestException.Reason?,
        action: SpeedTestFailureAction,
    ): Boolean {
        val failed = current.value as? SpeedTestUiState.Failed ?: return false
        if (expectedGeneration != generation || expectedReason != failed.reason || failureActionGeneration == generation) return false
        failureActionGeneration = generation
        telemetry.failureAction(failed.reason, action)
        return when (action) {
            SpeedTestFailureAction.Cancel -> true
            SpeedTestFailureAction.Retry -> { start(); false }
        }
    }

    private companion object {
        val START = SpeedTestProgress(SpeedTestPhase.Connecting, 0f, SpeedTestMetrics(), null)
    }
}
