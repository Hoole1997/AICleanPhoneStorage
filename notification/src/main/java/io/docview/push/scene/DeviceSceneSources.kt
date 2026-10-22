package io.docview.push.scene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import io.docview.push.utils.Logger
import kotlinx.coroutines.*

/** 前台服务拥有的电池广播和默认网络回调；不在 Application 再注册一份，不轮询设备。 */
internal class DeviceSceneSources(
    private val context: Context,
    private val scope: CoroutineScope,
    private val now: () -> SceneMoment,
    private val emit: (SceneSignals) -> Unit,
) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var battery = BatterySceneRules()
    private var network = NetworkSceneRules(SceneNetworkState(SceneNetwork.UNKNOWN))
    private var closed = false
    private data class BatterySignal(val value: SceneBattery?, val action: String?, val revision: Long)
    private var batteryRevision = 0L
    private val batterySignals = kotlinx.coroutines.channels.Channel<BatterySignal>(kotlinx.coroutines.channels.Channel.CONFLATED)
    init {
        scope.launch {
            for (signal in batterySignals) {
                val sample = try { signal.value ?: withContext(Dispatchers.IO) { readBattery() } ?: continue }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: RuntimeException) { Logger.w("场景推送：读取电池状态失败，本次广播已跳过，异常类型=${error.javaClass.simpleName}"); continue }
                if (signal.revision != batteryRevision || batteryReceiver == null || closed) continue
                emit(battery.update(sample, now(), signal.action == Intent.ACTION_BATTERY_LOW, signal.action == Intent.ACTION_BATTERY_OKAY))
            }
        }
    }

    suspend fun refresh(config: SceneConfiguration) {
        if (closed) return
        val batteryEnabled = listOf(PushScene.CHARGE_START, PushScene.CHARGE_END, PushScene.LOW_BATTERY).any(config::enabled)
        if (!batteryEnabled) stopBattery() else if (batteryReceiver == null) {
            val baseline = withContext(Dispatchers.IO) { readBattery() }
            if (closed) return
            battery = BatterySceneRules()
            baseline?.let { battery.update(it, now()) }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (batteryReceiver !== this || closed) return
                    val action = intent.action
                    val name = when (action) {
                        Intent.ACTION_POWER_CONNECTED -> "电源接入"
                        Intent.ACTION_POWER_DISCONNECTED -> "电源断开"
                        Intent.ACTION_BATTERY_LOW -> "低电量"
                        Intent.ACTION_BATTERY_OKAY -> "电量恢复"
                        else -> null // 高频电量快照不逐次打印，只记录真正生成的候选。
                    }
                    if (name != null) Logger.d("场景推送：收到$name 广播，开始检查电池场景条件")
                    val value = if (action == Intent.ACTION_BATTERY_CHANGED) sample(intent) else null
                    batterySignals.trySend(BatterySignal(value, action, ++batteryRevision))
                }
            }
            batteryReceiver = receiver
            try {
                ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_BATTERY_OKAY)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                Logger.d("场景推送：电池广播监听已注册，初始快照只建立基线")
            } catch (error: RuntimeException) { batteryReceiver = null; Logger.w("场景推送：电池广播注册失败，异常类型=${error.javaClass.simpleName}") }
        }
        if (!config.enabled(PushScene.NETWORK)) stopNetwork() else if (networkCallback == null) {
            val baseline = withContext(Dispatchers.IO) {
                try {
                    val active = connectivity.activeNetwork
                    SceneNetworkState(if (active == null) SceneNetwork.OFFLINE else kind(connectivity.getNetworkCapabilities(active)), active?.toString().orEmpty())
                } catch (error: RuntimeException) { Logger.w("场景推送：读取初始网络状态失败，等待后续系统回调，异常类型=${error.javaClass.simpleName}"); SceneNetworkState(SceneNetwork.UNKNOWN) }
            }
            if (closed) return
            network = NetworkSceneRules(baseline)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(value: Network, capabilities: NetworkCapabilities) {
                    val snapshot = SceneNetworkState(kind(capabilities), value.toString())
                    val self = this
                    scope.launch { if (networkCallback === self && !closed) emit(network.update(snapshot, now())) }
                }
                override fun onLost(value: Network) {
                    val self = this
                    scope.launch {
                        if (networkCallback === self && !closed && network.current.identity == value.toString())
                            emit(network.update(SceneNetworkState(SceneNetwork.OFFLINE), now()))
                    }
                }
            }
            networkCallback = callback
            try { connectivity.registerDefaultNetworkCallback(callback); Logger.d("场景推送：默认网络变化监听已注册") }
            catch (error: RuntimeException) { networkCallback = null; Logger.w("场景推送：网络监听注册失败，异常类型=${error.javaClass.simpleName}") }
        }
    }

    fun valid(event: SceneEvent) = if (event.scene == PushScene.NETWORK) network.valid(event) else battery.valid(event)
    fun fresh(event: SceneEvent) = when (event.scene) {
        PushScene.LOW_BATTERY -> event.copy(batteryPercent = battery.current?.percent)
        PushScene.CHARGE_START, PushScene.CHARGE_END -> event.copy(charging = battery.current?.charging, plugged = battery.current?.plugged)
        else -> event
    }
    private fun readBattery() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::sample)
    private fun sample(intent: Intent): SceneBattery {
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val percent = if (scale > 0 && level in 0..scale) (level * 100L / scale).toInt() else null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return SceneBattery(percent, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
            status == BatteryManager.BATTERY_STATUS_CHARGING, status == BatteryManager.BATTERY_STATUS_FULL)
    }
    private fun kind(caps: NetworkCapabilities?): SceneNetwork = when {
        caps == null -> SceneNetwork.UNKNOWN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> SceneNetwork.VPN
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SceneNetwork.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SceneNetwork.MOBILE
        else -> SceneNetwork.OTHER
    }
    private fun stopBattery() { batteryRevision++; val old = batteryReceiver; batteryReceiver = null; if (old != null) { runCatching { context.unregisterReceiver(old) }; Logger.d("场景推送：电池广播监听已释放") } }
    private fun stopNetwork() { val old = networkCallback; networkCallback = null; if (old != null) { runCatching { connectivity.unregisterNetworkCallback(old) }; Logger.d("场景推送：网络变化监听已释放") } }
    override fun close() { closed = true; stopBattery(); stopNetwork(); batterySignals.close() }
}
