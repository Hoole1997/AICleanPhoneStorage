package com.example.aicleanphonestorage.feature.networktraffic.data

import android.content.Context
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidNetworkTrafficRepository(context: Context, private val executor: TaskExecutor) : NetworkTrafficRepository {
    private val access = UsageAccessChecker(context)
    private val statistics = AndroidTrafficDataSource(context)
    private val metadata = AppMetadataDataSource(context)
    private val periods = TrafficPeriodResolver()
    // 应用容器只创建一个 Repository。取消无法强杀 Binder，因此许可保持到旧系统调用真正返回。
    private val queryLock = Mutex()

    override suspend fun hasUsageAccess(): Boolean = executor.io { access.isGranted() }

    override suspend fun load(period: TrafficPeriod, onProgress: (TrafficProgress) -> Unit): TrafficSnapshot = queryLock.withLock {
        executor.io {
            if (!access.isGranted()) throw SecurityException("Usage access required")
            val window = periods.resolve(period)
            onProgress(TrafficProgress(TrafficStage.MOBILE))
            val mobile = statistics.readMobile(window)
            onProgress(TrafficProgress(TrafficStage.WIFI))
            val wifi = statistics.readWifi(window)
            if (mobile.usage.bytes == null && wifi.usage.bytes == null) throw IOException("Network history unavailable")
            val uids = (mobile.uids.keys + wifi.uids.keys).filter {
                addBytes(mobile.uids[it] ?: 0, wifi.uids[it] ?: 0) > 0
            }
            val apps = ArrayList<TrafficApp>(uids.size)
            var lastPercent = -1
            for ((index, uid) in uids.withIndex()) {
                currentCoroutineContext().ensureActive()
                apps += TrafficApp(uid, metadata.applicationsForUid(uid), mobile.uids[uid] ?: 0, wifi.uids[uid] ?: 0)
                val progress = TrafficProgress(TrafficStage.APPLICATIONS, index + 1, uids.size)
                // 最多100个进度变化，不为每个 bucket 发出 UI 更新，也不人为延长 Loading。
                if (progress.percent != lastPercent) { onProgress(progress); lastPercent = progress.percent ?: -1 }
            }
            currentCoroutineContext().ensureActive()
            TrafficSnapshot(period, window, mobile.usage, wifi.usage, apps.sortedWith(compareByDescending<TrafficApp> { it.bytes }.thenBy { it.uid }))
        }
    }
}
