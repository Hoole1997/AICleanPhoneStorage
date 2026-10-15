package com.example.aicleanphonestorage.app.risk

import android.app.Application
import android.util.Log
import com.android.common.bill.BillConfig
import com.android.common.risk.AdRisk
import com.example.aicleanphonestorage.BuildConfig
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.corekit.core.controller.ChannelUserController

/**
 * Application 的唯一装配点：SDK/策略/遥测各自独立，页面只等待首次有期限的决策。
 * 各渠道由自身开关选择 SDK；不持 Activity，不跟随热启动重复检测或轮询设备。
 */
internal object DeviceRiskRuntime {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var coordinator: DeviceRiskCoordinator? = null

    fun initialize(application: Application, telemetryReady: Deferred<Boolean>) {
        synchronized(lock) {
            if (coordinator != null) return
            val telemetry = DeviceRiskTelemetry()
            val bridge = DeviceRiskPolicyBridge(
                scope, AdRisk.signalConfig, AdRisk::updatePlatformRestriction, telemetry::reportAdBlock,
            )
            val sdk = try {
                createDeviceRiskSdk(application, telemetry::reportXmEvent)
            } catch (_: Exception) {
                unavailableSdk()
            } catch (_: LinkageError) {
                unavailableSdk()
            }
            val enabled = BuildConfig.XM_RISK_ENABLED && BuildConfig.XM_RISK_BASE_URL.isNotBlank()
            val detector = DeviceRiskCoordinator(
                scope = scope,
                detect = {
                    if (!enabled) throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.CONFIGURATION)
                    // 定向事件无 Reporter 会直接丢弃，先等待注册；失败/超时则不启动采集与检测。
                    val ready = withTimeoutOrNull(TELEMETRY_WAIT_MS) { telemetryReady.await() }
                    if (ready != true) throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.ANALYTICS)
                    sdk.detect()
                },
                apply = { snapshot ->
                    bridge.apply(snapshot)
                    if (!bridge.awaitSynchronized()) throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.POLICY)
                },
                release = bridge::releaseForUnavailable,
                reportStatus = { status, reason ->
                    Log.i(TAG, "Device risk: status=$status reason=${reason?.name.orEmpty()}")
                },
            )
            coordinator = detector
            scope.launch {
                // 策略同步成功也可能刚好越过检测截止时间；只有最终 APPLIED 才开启独立遥测。
                // Reporter 工作不参与 15 秒门禁，失败/超时终态永远不开启此门闩。
                if (detector.state.first { it != DeviceRiskStatus.WAITING && it != DeviceRiskStatus.DETECTING } ==
                    DeviceRiskStatus.APPLIED) bridge.confirmInitialDecision()
            }
            if (enabled) {
                installCollectAdCallbacks(application)
                uploadForPaidUser(sdk, telemetryReady)
            }
            detector.start()
        }
    }

    /** Activity 等待者取消只停止本页等待，不取消其他页面共用的本进程检测。 */
    suspend fun awaitInitialDecision() {
        coordinator?.awaitInitialDecision()
    }

    private fun unavailableSdk(): DeviceRiskSdk = object : DeviceRiskSdk {
        override suspend fun detect(): DeviceRiskSnapshot =
            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR)
        override fun uploadDeviceInfo() = Unit
    }

    private fun installCollectAdCallbacks(application: Application) {
        val broadcaster = CollectAdEventBroadcaster(application)
        // Bill 是单回调槽：组合已有处理，收益仍由 RevenueAdManager 上报，不能再报一遍收益。
        val previousRevenue = BillConfig.onAdRevenue
        val previousClick = BillConfig.onAdClick
        BillConfig.onAdRevenue = { data ->
            try { previousRevenue?.invoke(data) } finally { broadcaster.sendImpression(data) }
        }
        BillConfig.onAdClick = { data ->
            try { previousClick?.invoke(data) } finally { broadcaster.sendClick(data) }
        }
    }

    private fun uploadForPaidUser(sdk: DeviceRiskSdk, telemetryReady: Deferred<Boolean>) {
        scope.launch {
            // 只消费首次已锁定归因。自然量不调用显式设备上报，未锁定最多等待 30 秒。
            val paid = withTimeoutOrNull(ATTRIBUTION_WAIT_MS) {
                if (!telemetryReady.await()) return@withTimeoutOrNull false
                awaitLockedChannel()
            }
            if (paid != true) return@launch
            try { sdk.uploadDeviceInfo() }
            catch (_: Exception) { Log.w(TAG, "Collect device upload unavailable") }
            catch (_: LinkageError) { Log.w(TAG, "Collect device upload entry unavailable") }
        }
    }

    private suspend fun awaitLockedChannel(): Boolean {
        if (ChannelUserController.isChannelSetOnce()) return ChannelUserController.isPaidChannel()
        val result = CompletableDeferred<Boolean>()
        val pending = AtomicReference<CompletableDeferred<Boolean>?>(result)
        val listener = object : ChannelUserController.ChannelChangeListener {
            override fun onChannelChanged(
                oldChannel: ChannelUserController.UserChannelType,
                newChannel: ChannelUserController.UserChannelType,
            ) {
                pending.get()?.complete(newChannel == ChannelUserController.UserChannelType.PAID)
            }
        }
        try {
            ChannelUserController.addChannelChangeListener(listener)
            // 注册和读取之间可能刚好锁定；已收到回调的 Deferred 不会被旧 getter 覆盖。
            if (ChannelUserController.isChannelSetOnce()) result.complete(ChannelUserController.isPaidChannel())
            return result.await()
        } finally {
            // SDK 在任意调用线程用原始 ArrayList 遍历监听器，移除可能影响正在派发的其他监听。
            // 每进程最多留下一个不捕获 Context 的空门闩；结束/超时清掉协程引用，后续回调无工作。
            pending.set(null)
        }
    }

    private const val TAG = "CleanDeviceRisk"
    private const val TELEMETRY_WAIT_MS = 5_000L
    private const val ATTRIBUTION_WAIT_MS = 30_000L
}
