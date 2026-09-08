package com.example.aicleanphonestorage.feature.networktraffic.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.RemoteException
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class NetworkRead(val usage: NetworkUsage, val uids: Map<Int, Long>)

/** 仅由 Repository 的 I/O 区域调用。原始 bucket 边遍历边汇总，绝不保留全量记录。 */
internal class AndroidTrafficDataSource(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NetworkStatsManager::class.java)

    @Suppress("DEPRECATION")
    suspend fun readMobile(window: TrafficWindow,includeApplications:Boolean=true): NetworkRead {
        if (Build.VERSION.SDK_INT >= 29) return read(ConnectivityManager.TYPE_MOBILE, listOf(null), window,includeApplications)
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return NetworkRead(NetworkUsage.unavailable(UsageAvailability.PHONE_PERMISSION_REQUIRED), emptyMap())
        val ids = try { legacySubscriberIds() } catch (error: SecurityException) {
            return NetworkRead(NetworkUsage.unavailable(UsageAvailability.PHONE_PERMISSION_REQUIRED), emptyMap())
        }
        if (ids.isEmpty()) return NetworkRead(NetworkUsage.unavailable(UsageAvailability.NO_SIM), emptyMap())
        return read(ConnectivityManager.TYPE_MOBILE, ids, window,includeApplications)
    }

    @Suppress("DEPRECATION")
    suspend fun readWifi(window: TrafficWindow,includeApplications:Boolean=true): NetworkRead = read(ConnectivityManager.TYPE_WIFI, listOf(null), window,includeApplications)

    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    private fun legacySubscriberIds(): List<String> {
        // 只在 API 24–28 且已授权的路径读取；ID 不缓存、不落盘、不记录日志。
        val telephony = app.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val subscriptions = app.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        return subscriptions.mapNotNull { telephony.createForSubscriptionId(it.subscriptionId).subscriberId }
            .filter { it.isNotEmpty() }.distinct()
    }

    @SuppressLint("MissingPermission")
    private suspend fun read(type: Int, subscriberIds: List<String?>, window: TrafficWindow,includeApplications:Boolean): NetworkRead {
        val statsManager = manager ?: return unavailable()
        if (window.startMillis == window.endMillis) return NetworkRead(NetworkUsage(0, UsageAvailability.AVAILABLE), emptyMap())
        val byUid = HashMap<Int, Long>()
        var total = 0L
        try {
            for (subscriber in subscriberIds) {
                currentCoroutineContext().ensureActive()
                // 顶部与应用统计都限定当前用户，避免工作资料/其他用户混入分母。
                val summary = statsManager.querySummaryForUser(type, subscriber, window.startMillis, window.endMillis)
                    ?: return unavailable()
                total = addBytes(total, addBytes(summary.rxBytes, summary.txBytes))
                currentCoroutineContext().ensureActive()
                if(!includeApplications)continue
                val stats = statsManager.querySummary(type, subscriber, window.startMillis, window.endMillis)
                    ?: return unavailable()
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        currentCoroutineContext().ensureActive()
                        if (stats.getNextBucket(bucket)) {
                            byUid[bucket.uid] = addBytes(byUid[bucket.uid] ?: 0, addBytes(bucket.rxBytes, bucket.txBytes))
                        }
                    }
                } finally {
                    stats.close()
                }
            }
        } catch (error: RemoteException) {
            return unavailable()
        } catch (error: IllegalArgumentException) {
            // 无对应网络模板/不支持的 OEM 接口，保留另一种网络可用结果。
            return unavailable()
        }
        return NetworkRead(NetworkUsage(total, UsageAvailability.AVAILABLE), byUid)
    }

    private fun unavailable() = NetworkRead(NetworkUsage.unavailable(UsageAvailability.UNAVAILABLE), emptyMap())
}
