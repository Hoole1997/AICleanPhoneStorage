package com.example.aicleanphonestorage.feature.home.data

import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.ScanIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest

/** 聚合最近已完成的四类扫描索引，不在首页再次遍历文件系统。选择/筛选不会改变入口的总占用。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeFileMetricsSource(
    private val index: ScanIndex,
    private val executor: TaskExecutor,
) {
    fun observe() =
        index.changes
            .mapLatest { executor.io { read() } }
            .catch {
                if (it is android.database.SQLException)
                    emit(
                        HomeToolMetrics(
                            compress = HomeToolMetric.Unavailable,
                            largeFiles = HomeToolMetric.Unavailable,
                            unusedFiles = HomeToolMetric.Unavailable,
                            screenshots = HomeToolMetric.Unavailable,
                        )
                    )
                else throw it
            }

    fun read(): HomeToolMetrics {
        val values = mutableMapOf<String, HomeToolMetric>()
        index.readableDatabase
            .rawQuery(
                """
                SELECT s.feature,TOTAL(f.size)
                FROM scans s LEFT JOIN files f ON f.scan=s.id
                WHERE s.id IN (SELECT MAX(id) FROM scans WHERE ready=1 GROUP BY feature)
                  AND s.feature IN ('PHOTO_COMPRESS','LARGE_FILES','UNUSED_FILES','SCREENSHOTS')
                GROUP BY s.id,s.feature
                """
                    .trimIndent(),
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) values[cursor.getString(0)] =
                    HomeToolMetric.Bytes(cursor.getDouble(1).toLong().coerceAtLeast(0))
            }
        return HomeToolMetrics(
            compress = values["PHOTO_COMPRESS"] ?: HomeToolMetric.NotScanned,
            largeFiles = values["LARGE_FILES"] ?: HomeToolMetric.NotScanned,
            unusedFiles = values["UNUSED_FILES"] ?: HomeToolMetric.NotScanned,
            screenshots = values["SCREENSHOTS"] ?: HomeToolMetric.NotScanned,
        )
    }
}
