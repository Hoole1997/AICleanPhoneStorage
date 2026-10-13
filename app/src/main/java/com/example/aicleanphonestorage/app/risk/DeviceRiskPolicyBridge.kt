package com.example.aicleanphonestorage.app.risk

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.risk.AdRestrictionScope
import com.android.common.risk.AdRiskSignalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 将一次设备检测与最新远程策略聚合为 PROCESS 平台限制；配置变化只重算策略，不再检测设备。
 * 生命周期属于注入的应用 scope。此桥接应是宿主的 PROCESS 限制写入者，SDK 不提供按来源撤销 API。
 */
internal class DeviceRiskPolicyBridge(
    scope: CoroutineScope,
    signalConfig: Flow<AdRiskSignalConfig>,
    private val updateRestriction: (AdPlatform, AdRestrictionScope, Boolean) -> Boolean,
) {
    private data class Target(
        val version: Long,
        val ready: Boolean,
        val signals: Map<DeviceRiskSignal, Int>,
        val platforms: Set<AdPlatform>,
        val attempted: Int = 0,
    )

    private data class Synchronization(val version: Long, val settled: Boolean, val successful: Boolean)

    private val lock = Any()
    private var snapshot: DeviceRiskSnapshot? = null
    private var config = AdRiskSignalConfig()
    private var configurationReady = false
    // 只记录本桥接成功设置的限制；失败/超时不能清除未由本桥接设置的平台。
    private val applied = mutableSetOf<AdPlatform>()
    private val targets = MutableStateFlow(Target(0, true, emptyMap(), emptySet()))
    private val synchronization = MutableStateFlow(Synchronization(0, settled = true, successful = true))

    init {
        scope.launch {
            signalConfig.catch { releaseForUnavailable() }.collect { latest ->
                synchronized(lock) {
                    config = latest
                    configurationReady = true
                    publishTargetLocked()
                }
            }
        }
        scope.launch {
            // collectLatest 取消旧等待，且会等旧同步结束后再执行新目标，不并发写 SDK。
            targets.collectLatest(::synchronize)
        }
    }

    fun apply(snapshot: DeviceRiskSnapshot) {
        synchronized(lock) {
            this.snapshot = snapshot.copy(detectedSignals = snapshot.detectedSignals.toSet())
            publishTargetLocked()
        }
    }

    /** 首条广告等待当前目标：新版本会继续等待；false 表示本轮有限重试耗尽，不表示成功。 */
    suspend fun awaitSynchronized(): Boolean {
        while (true) {
            val result = synchronization.first { it.settled }
            synchronized(lock) {
                if (result.version == targets.value.version) return result.successful
            }
        }
    }

    /** 检测失败/超时先立即撤销本桥接的内存限制，未成功清除的项交由有限重试补发。 */
    fun releaseForUnavailable() {
        synchronized(lock) {
            snapshot = null
            val attempted = if (applied.isEmpty()) 0 else 1
            applied.toList().forEach { platform -> updateLocked(platform, restricted = false) }
            // 立即清除也算第一次尝试，补发只使用剩余预算；先清除再发布，避免双重首次写入。
            publishTargetLocked(attempted)
            if (applied.isEmpty()) settleLocked(targets.value, successful = true)
        }
    }

    /** 只返回最新检测/配置中实际已限制平台的维度；真实广告请求时机由宿主负责上报。 */
    fun blockedSignals(): Map<DeviceRiskSignal, Int> = synchronized(lock) {
        targets.value.signals.filterValues { policy -> platformsFor(policy).any { it in applied } }
    }

    private fun publishTargetLocked(attempted: Int = 0) {
        val signals = snapshot?.detectedSignals.orEmpty().associateWith(::policyFor)
            .filterValues { it == AdRiskSignalConfig.BLOCK_ADMOB || it == AdRiskSignalConfig.BLOCK_ALL_ADS }
        val platforms = signals.values.flatMap(::platformsFor).toSet()
        val next = Target(
            version = targets.value.version + 1,
            ready = snapshot == null || configurationReady,
            signals = signals,
            platforms = platforms,
            attempted = attempted,
        )
        synchronization.value = Synchronization(next.version, settled = false, successful = false)
        targets.value = next
    }

    private suspend fun synchronize(target: Target) {
        if (!target.ready) return
        synchronized(lock) {
            if (targets.value.version != target.version) return
            if (applied == target.platforms) {
                settleLocked(target, successful = true)
                return
            }
        }
        if (target.attempted > 0) delay(RETRY_INTERVAL_MS)
        repeat(MAX_ATTEMPTS - target.attempted) { remainingAttempt ->
            val attempt = remainingAttempt + target.attempted
            currentCoroutineContext().ensureActive()
            val changes = synchronized(lock) {
                if (targets.value.version != target.version) return
                // 先设置新增限制，再撤销不再需要的限制，多风险聚合不会提前解除同一平台。
                (target.platforms - applied).map { it to true } +
                    (applied - target.platforms).map { it to false }
            }
            for ((platform, restricted) in changes) {
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (targets.value.version != target.version) return
                    updateLocked(platform, restricted)
                }
            }
            val finished = synchronized(lock) {
                if (targets.value.version != target.version) return
                (applied == target.platforms).also { successful ->
                    if (successful || attempt == MAX_ATTEMPTS - 1) settleLocked(target, successful)
                }
            }
            if (finished) return
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_INTERVAL_MS)
        }
    }

    private fun updateLocked(platform: AdPlatform, restricted: Boolean) {
        // PROCESS API 仅更新 SDK 内存；持锁使超时释放和迟到的旧 worker 写入互斥，不做 I/O。
        val successful = try {
            updateRestriction(platform, AdRestrictionScope.PROCESS, restricted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (successful) {
            if (restricted) applied.add(platform) else applied.remove(platform)
        }
    }

    private fun settleLocked(target: Target, successful: Boolean) {
        synchronization.value = Synchronization(target.version, settled = true, successful = successful)
    }

    private fun policyFor(signal: DeviceRiskSignal): Int = when (signal) {
        DeviceRiskSignal.VPN -> config.vpnAdConfig
        DeviceRiskSignal.DNS -> config.dnsAdConfig
        DeviceRiskSignal.IP -> config.ipAdConfig
        DeviceRiskSignal.SIMULATOR -> config.simulatorAdConfig
        DeviceRiskSignal.ADB -> config.adbAdConfig
        DeviceRiskSignal.NO_SIM -> config.simAdConfig
        DeviceRiskSignal.NO_GP -> config.noGpAdConfig
        DeviceRiskSignal.SYS_9_LOW -> config.sys9LowAdConfig
        DeviceRiskSignal.PLAY -> config.playAdConfig
    }

    private fun platformsFor(policy: Int): Set<AdPlatform> = when (policy) {
        AdRiskSignalConfig.BLOCK_ADMOB -> setOf(AdPlatform.ADMOB)
        AdRiskSignalConfig.BLOCK_ALL_ADS -> AdPlatform.entries.toSet()
        else -> emptySet()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val RETRY_INTERVAL_MS = 1_000L
    }
}
