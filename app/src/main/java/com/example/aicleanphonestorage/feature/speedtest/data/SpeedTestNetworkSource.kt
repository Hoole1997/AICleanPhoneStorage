package com.example.aicleanphonestorage.feature.speedtest.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * 读取当前网络概况，只用无需额外权限的公开 API：
 * 不读取 Wi-Fi 名称（需要位置权限），蜂窝网络只取系统公开的运营商显示名。
 * 调用方需在后台线程调用（Binder 查询）。
 */
internal class SpeedTestNetworkSource(context: Context) {
    private val app = context.applicationContext

    fun hasInternet(): Boolean = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

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
