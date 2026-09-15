package com.example.aicleanphonestorage.feature.junkcleaner.data

import android.os.Environment
import android.os.StatFs
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.home.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest

internal data class JunkSnapshot(
    val handle: ScanHandle?,
    val categories: List<JunkCategorySummary>,
    val storage: StorageSummary,
)

/** 只观察常量大小的分类/首页摘要；没有定时扫描，订阅停止后不再进行 I/O。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class JunkSummaryRepository(
    private val index: ScanIndex,
    private val executor: TaskExecutor,
) : HomeOverviewRepository {
    private val groups = JunkIndex(index)

    fun snapshots(scan: Long) =
        index.changes.mapLatest {
            executor.io { JunkSnapshot(index.handle(scan), groups.visibleCategories(scan), storage()) }
        }

    override fun observeOverview() =
        index.changes.mapLatest {
            executor.io {
                val latest = groups.latest()
                HomeOverview(
                    storage(),
                    latest?.let { (handle, time) ->
                        ScanSummary.Completed(groups.visibleCategories(handle.id).sumOf { it.bytes }, time)
                    } ?: ScanSummary.NotScanned,
                )
            }
        }

    private fun storage(): StorageSummary {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return StorageSummary(
            stat.totalBytes,
            (stat.totalBytes - stat.availableBytes).coerceIn(0, stat.totalBytes),
        )
    }
}
