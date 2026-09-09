package com.example.aicleanphonestorage.feature.home.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.networktraffic.data.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HomePlatformMetrics(
    val network: HomeToolMetric = HomeToolMetric.Reading,
    val apps: HomeToolMetric = HomeToolMetric.Reading,
    val wifiBytes: Long? = null,
)

/** 每次首页订阅时读取系统摘要，串行占用一个 I/O 许可；不查应用明细，不轮询或请求新权限。 */
internal class HomePlatformMetricsSource(context: Context, private val executor: TaskExecutor) {
    private val app = context.applicationContext
    private val access = UsageAccessChecker(app)
    private val traffic = AndroidTrafficDataSource(app)
    private val periods = TrafficPeriodResolver()
    private val lock = Mutex()
    private var cached: HomePlatformMetrics? = null
    private var cachedAccess = false
    private var cachedMonth = 0L

    fun observe() = flow {
        if (cached == null) emit(HomePlatformMetrics())
        lock.withLock {
            val granted = executor.io { access.isGranted() }
            val window = periods.resolve(TrafficPeriod.THIS_MONTH)
            if (granted == cachedAccess && cachedMonth == window.startMillis)
                cached?.let { emit(it) }
            // 复用同一次本月摘要读取；未知/未授权用 null，与实际 0 字节区分。
            var wifiBytes: Long? = null
            val network =
                if (!granted) HomeToolMetric.AccessRequired
                else
                    safely {
                        executor.io {
                            val mobile =
                                traffic.readMobile(window, includeApplications = false).usage
                            val wifi = traffic.readWifi(window, includeApplications = false).usage
                            wifiBytes = wifi.bytes
                            if (mobile.bytes == null && wifi.bytes == null)
                                HomeToolMetric.Unavailable
                            else
                                HomeToolMetric.Bytes(
                                    addBytes(mobile.bytes ?: 0, wifi.bytes ?: 0),
                                    partial = mobile.bytes == null || wifi.bytes == null,
                                )
                        }
                    }
            emit(
                HomePlatformMetrics(
                    network,
                    cached?.takeIf { cachedAccess == granted }?.apps ?: HomeToolMetric.Reading,
                    wifiBytes = wifiBytes,
                )
            )
            val apps = safely { executor.io { readApps(granted) } }
            currentCoroutineContext().ensureActive()
            val result = HomePlatformMetrics(network, apps, wifiBytes)
            cached = result
            cachedAccess = granted
            cachedMonth = window.startMillis
            emit(result)
        }
    }

    private suspend fun readApps(granted: Boolean): HomeToolMetric {
        if (granted && Build.VERSION.SDK_INT >= 26) {
            val manager = app.getSystemService(StorageStatsManager::class.java)
            try {
                // 系统按当前用户汇总，避免逐包查询以及 shared UID 重复累加。
                val stats =
                    manager?.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
                currentCoroutineContext().ensureActive()
                if (stats != null && stats.appBytes >= 0 && stats.dataBytes >= 0)
                    return HomeToolMetric.Bytes(
                        addBytes(stats.appBytes, stats.dataBytes)
                    ) // dataBytes 已包含 cacheBytes。
            } catch (error: SecurityException) {
                /* 无权读取占用时显示可管理应用数。 */
            } catch (error: IOException) {
                /* 某些 OEM 不提供用户级统计，采用明确的数量回退。 */
            } catch (error: UnsupportedOperationException) {
                /* 无统计能力时回退数量。 */
            }
        }
        currentCoroutineContext().ensureActive()
        @Suppress("DEPRECATION")
        val packages =
            app.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            )
        return HomeToolMetric.AppCount(packages.map { it.activityInfo.packageName }.toSet().size)
    }

    private suspend fun safely(block: suspend () -> HomeToolMetric): HomeToolMetric =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            HomeToolMetric.AccessRequired
        } catch (_: IOException) {
            HomeToolMetric.Unavailable
        } catch (_: android.os.RemoteException) {
            HomeToolMetric.Unavailable
        }
}
