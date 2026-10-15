package com.example.aicleanphonestorage.app.risk

import android.app.Application
import com.ai.clean.phone.storage.junk.cleaner.ccefecb.RiskDetectionResult
import com.ai.clean.phone.storage.junk.cleaner.ccefecb.aeacefbcddb
import com.ai.clean.phone.storage.junk.cleaner.dckx.SafeObserverBuilder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 产品 Collect → XM 适配器；所有已启用风控的渠道共用真实入口映射。
 *
 * 加固入口是 native，同步返回时间不能由协程强制中断。因此入口放在单线程、有界队列
 * worker，宿主只挂起等待回调；超时取消立即释放 continuation 并忽略迟到结果。这里
 * 不使用 withContext 等待不可取消的 native 返回，也不强杀、打断 SDK 内部线程。
 */
internal class XmDeviceRiskSdk(
    private val application: Application,
    private val baseUrl: String,
    private val eventSink: (String, Map<String, *>) -> Unit,
) : DeviceRiskSdk {
    private val detectionStarted = AtomicBoolean(false)
    private val uploadStarted = AtomicBoolean(false)
    private val integrityFailed = AtomicBoolean(false)

    // 一次冷启动只有有限入口任务，最多一个 native 调用执行、两个等待。空闲线程会退出；
    // 若 SDK 自身永久阻塞，宿主仍可在总截止时间放行，不再追加任务或启动替代线程。
    private val worker = ThreadPoolExecutor(
        1,
        1,
        60L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(2),
        { task -> Thread(task, "device-risk-native").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    /** 由应用级协调器设定全链路总截止时间；这里仅给单次指纹获取设置 5 秒上限。 */
    override suspend fun detect(): DeviceRiskSnapshot {
        if (!detectionStarted.compareAndSet(false, true)) {
            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR)
        }
        val active = AtomicBoolean(true)
        val resultAccepted = AtomicBoolean(false)
        val resultMapping = AtomicReference<Future<*>?>(null)
        try {
            val fingerprint = withTimeoutOrNull(FINGERPRINT_TIMEOUT_MILLIS) {
                awaitNativeCallback<String>(active) { complete, _ ->
                    SafeObserverBuilder.postMessage(application, Consumer(complete))
                }
            }
            if (fingerprint.isNullOrBlank()) {
                throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.FINGERPRINT)
            }

            return awaitNativeCallback(active) { complete, fail ->
                // SDK 调试日志包含原始指纹、设备标识与远端正文，正式适配不打开该输出。
                aeacefbcddb.afbbcecfa(false)
                aeacefbcddb.ebfcebaefcbabbf { eventName, properties ->
                    // PI 失败后 SDK 仍可能上传并得到有效空 ID；该响应不能把超时升级为风险。
                    if (active.get() && eventName == "XM_PlayIntegrity_Fail") integrityFailed.set(true)
                    // SDK 首次结果后可能还有 Play Integrity 补报，事件出口与策略截止时间独立。
                    // 只保留有界数数透传；迟到检测结果仍由下方 active 门闩过滤，不改变已放行决策。
                    runCatching { eventSink(eventName, properties) }
                }
                // 冷进程的 clearCache 没有 Context，不能保证清掉尚未装载的持久化结果。
                // 先注册等待者，再由带 Application Context 的 refresh 创建首个检测会话；
                // SDK 的 refresh/restart 契约会忽略旧缓存，不先调用可能返回缓存的 init。
                aeacefbcddb.becebfbdedbf { result: RiskDetectionResult ->
                    if (active.get() && resultAccepted.compareAndSet(false, true)) {
                        try {
                            // SDK 回调线程未知，native getter 与有界 JSON 判定也不在主线程做。
                            val task = worker.submit {
                                if (active.get()) {
                                    runCatching { result.toSnapshot() }
                                        .onSuccess(complete)
                                        .onFailure { fail() }
                                }
                            }
                            resultMapping.set(task)
                            if (!active.get()) cancelQueued(task)
                        } catch (_: RuntimeException) {
                            fail()
                        }
                    }
                }
                // 结果回调支持首个会话创建前注册，不把取得结果绑到 XM_SDK_Init 埋点事件。
                // 宿主只允许本次冷启动一次检测；不额外安排 SDK 的远端重试窗口。
                aeacefbcddb.fdbfceeededfd(application, baseUrl, fingerprint, 0)
            }
        } finally {
            active.set(false)
            resultMapping.get()?.let(::cancelQueued)
        }
    }

    /** 仅由宿主确认买量归因后调用；一次进程最多提交一次，不以失败重试重复采集。 */
    override fun uploadDeviceInfo() {
        if (!uploadStarted.compareAndSet(false, true)) return
        try {
            worker.submit {
                // 没有公开取消接口；执行异步 native 入口且不把失败正文或设备数据写日志。
                runCatching { SafeObserverBuilder.invokeService(application) }
            }
        } catch (_: RuntimeException) {
            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR)
        }
    }

    /** 只读 SDK 公开结果字段，失败/空 ID 的正式判定由可单测的公共 mapper 统一处理。 */
    private fun RiskDetectionResult.toSnapshot(): DeviceRiskSnapshot {
        val remote = remoteInfoData
        return DeviceRiskResultMapper.map(
            isVpn = isVpn,
            isDnsAbnormal = isDnsAbnormal,
            isIpAbnormal = isIpAbnormal,
            isSimulator = isSimulator,
            isAdb = isAdb,
            isNoSim = isNoSim,
            isNoGp = isNoGp,
            isSys9Low = isSys9Low,
            isRemoteAbnormal = isRemoteAbnormal,
            deviceId = remote?.deviceId.orEmpty(),
            errorMessage = remote?.errorMessage.orEmpty(),
            rawResponse = remote?.rawResponse.orEmpty(),
            integrityUnavailable = integrityFailed.get(),
        )
    }

    /**
     * 提交一次 native 调用并仅等待它的首个回调。
     *
     * continuation 原子取出，防重复回调；取消时清空引用并取消尚未执行的排队任务，
     * SDK 即使保留回调也不会再持有调用方协程。cancel(false) 不打断不可控的 JNI。
     */
    private suspend fun <T> awaitNativeCallback(
        active: AtomicBoolean,
        invoke: ((T) -> Unit, () -> Unit) -> Unit,
    ): T = suspendCancellableCoroutine { continuation ->
        val pending = AtomicReference<CancellableContinuation<T>?>(continuation)
        val submitted = AtomicReference<Future<*>?>(null)
        continuation.invokeOnCancellation {
            pending.set(null)
            submitted.get()?.let(::cancelQueued)
        }
        val complete: (T) -> Unit = { value ->
            if (active.get()) {
                pending.getAndSet(null)?.let { waiting ->
                    if (waiting.isActive) waiting.resume(value)
                }
            }
        }
        val fail: () -> Unit = {
            pending.getAndSet(null)?.let { waiting ->
                if (waiting.isActive) {
                    waiting.resumeWithException(
                        DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR),
                    )
                }
            }
        }
        try {
            val task = worker.submit {
                if (active.get() && pending.get()?.isActive == true) {
                    try {
                        invoke(complete, fail)
                    } catch (_: Throwable) {
                        fail()
                    }
                }
            }
            submitted.set(task)
            // 取消可能发生在 submit 与保存 Future 之间，补取消这个有限竞态窗口。
            if (!continuation.isActive) {
                cancelQueued(task)
            }
        } catch (_: RuntimeException) {
            fail()
        }
    }

    /** 只撤销排队/宿主 Future；不打断 JNI，及时移除队列中的指纹或 SDK 结果引用。 */
    private fun cancelQueued(task: Future<*>) {
        task.cancel(false)
        if (task is Runnable) worker.remove(task)
    }

    private companion object {
        const val FINGERPRINT_TIMEOUT_MILLIS = 5_000L
    }
}
