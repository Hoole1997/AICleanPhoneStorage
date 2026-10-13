package com.example.aicleanphonestorage.app.risk

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal enum class DeviceRiskStatus { WAITING, DETECTING, APPLIED, FAILED_OPEN, TIMED_OUT }

/**
 * 每进程一次检测，首次广告只等待有期限的终态。SDK 适配器必须可取消等待，不能在本协程
 * 直接执行不可中断的 native 调用；超时后只释放策略，迟到结果不能重新施加限制。
 */
internal class DeviceRiskCoordinator(
    private val scope: CoroutineScope,
    private val detect: suspend () -> DeviceRiskSnapshot,
    private val apply: suspend (DeviceRiskSnapshot) -> Unit,
    private val release: () -> Unit,
    private val timeoutMillis: Long = 15_000L,
    private val reportStatus: (DeviceRiskStatus, DeviceRiskUnavailableReason?) -> Unit = { _, _ -> },
) {
    private val started = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val completed = CompletableDeferred<Unit>()
    private val current = MutableStateFlow(DeviceRiskStatus.WAITING)
    val state = current.asStateFlow()

    init { require(timeoutMillis > 0) }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val job = scope.launch {
            current.value = DeviceRiskStatus.DETECTING
            try {
                withTimeout(timeoutMillis) {
                    val snapshot = detect()
                    // 即使第三方吞掉取消，也不接受截止时间之后的结果。
                    currentCoroutineContext().ensureActive()
                    apply(snapshot)
                    currentCoroutineContext().ensureActive()
                }
                finish(DeviceRiskStatus.APPLIED, null)
            } catch (_: TimeoutCancellationException) {
                finish(DeviceRiskStatus.TIMED_OUT, DeviceRiskUnavailableReason.TIMEOUT)
            } catch (cancelled: CancellationException) {
                finish(DeviceRiskStatus.FAILED_OPEN, DeviceRiskUnavailableReason.CANCELLED)
                throw cancelled
            } catch (error: DeviceRiskUnavailableException) {
                finish(DeviceRiskStatus.FAILED_OPEN, error.reason)
            } catch (_: Exception) {
                finish(DeviceRiskStatus.FAILED_OPEN, DeviceRiskUnavailableReason.SDK_ERROR)
            } catch (_: LinkageError) {
                // 缺 ABI / native 入口不兼容同样属于检测不可用，不能阻断广告和应用启动。
                finish(DeviceRiskStatus.FAILED_OPEN, DeviceRiskUnavailableReason.SDK_ERROR)
            }
        }
        job.invokeOnCompletion {
            // scope 在 launch 执行前已取消时也必须释放等待者；没有轮询或页面级重试。
            if (!completed.isCompleted) finish(DeviceRiskStatus.FAILED_OPEN, DeviceRiskUnavailableReason.CANCELLED)
        }
    }

    suspend fun awaitInitialDecision() {
        start()
        completed.await()
    }

    private fun finish(status: DeviceRiskStatus, reason: DeviceRiskUnavailableReason?) {
        if (!terminal.compareAndSet(false, true)) return
        try {
            if (status != DeviceRiskStatus.APPLIED) release()
        } finally {
            current.value = status
            completed.complete(Unit)
            // 遥测失败不能改变广告是否可以继续的终态。
            runCatching { reportStatus(status, reason) }
        }
    }
}
