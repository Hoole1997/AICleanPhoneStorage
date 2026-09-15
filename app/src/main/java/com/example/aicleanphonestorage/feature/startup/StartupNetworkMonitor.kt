package com.example.aicleanphonestorage.feature.startup

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class StartupNetworkState { ONLINE, OFFLINE, UNKNOWN }

/** 只观察默认网络，不发探测请求；由启动页前台生命周期订阅，结束时注销系统回调。 */
internal class StartupNetworkMonitor(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val states = callbackFlow {
        var network: Network? = null
        var validated = false
        var blocked = false
        fun publish() { trySend(if (validated && !blocked) StartupNetworkState.ONLINE else StartupNetworkState.OFFLINE) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(value: Network) {
                network = value
                validated = false
                blocked = false
                // 网络切换先等紧随其后的 capabilities，避免拿上一条网络的状态判断新网络。
                trySend(StartupNetworkState.UNKNOWN)
            }
            override fun onCapabilitiesChanged(value: Network, capabilities: NetworkCapabilities) {
                if (network != value) return
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                publish()
            }
            override fun onLost(value: Network) {
                if (network != value) return
                network = null
                validated = false
                publish()
            }
            override fun onBlockedStatusChanged(value: Network, valueBlocked: Boolean) {
                if (network != value) return
                blocked = valueBlocked
                publish()
            }
        }
        var registered = false
        try {
            // 首次快照在注册之前读取；后续只使用回调参数，不在回调中同步查询易过期的网络信息。
            network = manager.activeNetwork
            val capabilities = network?.let(manager::getNetworkCapabilities)
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            publish()
            manager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (_: RuntimeException) {
            // 查询/注册失败不是已经确认断网，不以此跳过启动流程。
            trySend(StartupNetworkState.UNKNOWN)
        }
        awaitClose {
            if (registered) try { manager.unregisterNetworkCallback(callback) } catch (_: IllegalArgumentException) { }
        }
    }.distinctUntilChanged()
}
