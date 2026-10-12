package com.example.aicleanphonestorage.feature.speedtest.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 读取当前网络概况，只用无需额外权限的公开 API：
 * 不读取 Wi-Fi 名称（需要位置权限），蜂窝网络只取系统公开的运营商显示名。
 * 调用方需在后台线程调用（Binder 查询）。
 */
internal class SpeedTestNetworkSource(context: Context) : SpeedTestConnectivity {
    private val app = context.applicationContext

    override fun connectivity(): SpeedTestConnectivityState = try {
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val caps = if (manager != null && network != null) manager.getNetworkCapabilities(network) else null
        when {
            manager == null -> SpeedTestConnectivityState.Unavailable
            network == null || caps == null -> SpeedTestConnectivityState.Offline
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> SpeedTestConnectivityState.CaptivePortal
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> SpeedTestConnectivityState.Offline
            else -> SpeedTestConnectivityState.Online(network.networkHandle)
        }
    } catch (error: RuntimeException) {
        // SecurityException 等系统服务异常：无法判断网络状态，交由“网络信息失败”提示。
        SpeedTestConnectivityState.Unavailable
    }

    /** 事件驱动：注册默认网络回调，测速结束随收集取消立即注销，不轮询。 */
    override fun switchedFrom(networkId: Long): Flow<Unit> = callbackFlow {
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            // 注册时会先回调当前默认网络，句柄相同不算切换。
            override fun onAvailable(network: Network) {
                if (network.networkHandle != networkId) trySend(Unit)
            }
        }
        val registered = try {
            manager?.registerDefaultNetworkCallback(callback)
            manager != null
        } catch (error: RuntimeException) {
            // 回调数量超限等情况下放弃实时检测，出错时仍按当时网络状态归类。
            false
        }
        awaitClose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    fun current(): SpeedTestNetwork {
        val caps = capabilities()
        val type = when {
            caps == null -> SpeedTestNetworkType.Other
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SpeedTestNetworkType.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SpeedTestNetworkType.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> SpeedTestNetworkType.Ethernet
            else -> SpeedTestNetworkType.Other
        }
        return SpeedTestNetwork(type, if (type == SpeedTestNetworkType.Cellular) carrier() else null, Build.MODEL.orEmpty())
    }

    private fun capabilities(): NetworkCapabilities? =
        app.getSystemService(ConnectivityManager::class.java)?.let { manager ->
            try { manager.getNetworkCapabilities(manager.activeNetwork) } catch (error: SecurityException) { null }
        }

    private fun carrier(): String? = try {
        val telephony = app.getSystemService(TelephonyManager::class.java)
        // 双卡设备取默认数据卡；无效 ID 时退回系统默认实例。
        val data = SubscriptionManager.getDefaultDataSubscriptionId()
        val scoped = if (data != SubscriptionManager.INVALID_SUBSCRIPTION_ID) telephony?.createForSubscriptionId(data) else telephony
        scoped?.networkOperatorName?.trim()?.takeIf { it.isNotEmpty() }
    } catch (error: RuntimeException) {
        // 部分 OEM 在无 SIM 或系统服务异常时抛出运行时异常；运营商名称只是展示信息。
        null
    }
}
