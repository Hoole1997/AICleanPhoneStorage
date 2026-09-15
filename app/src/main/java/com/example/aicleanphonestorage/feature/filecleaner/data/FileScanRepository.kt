package com.example.aicleanphonestorage.feature.filecleaner.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.scan.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 四种清理共享扫描、批量写索引、分页和选择；扫描是只读操作，真正写文件由操作引擎负责。 */
internal class FileScanRepository(
    context: Context, private val executor: TaskExecutor,
    private val telemetry: com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry = com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry(),
) {
    val index = ScanIndex(context)
    val access = CleanupAccess(context)
    private val junkIndex =
        com.example.aicleanphonestorage.feature.junkcleaner.data.JunkIndex(index)
    private val sources = FileScanSources(context, index)
    private val scanLock = Mutex()

    suspend fun resolveAccess(feature: CleanupFeature) = executor.io { access.resolve(feature) }

    suspend fun rememberTree(uri: String) = access.rememberTree(uri)

    suspend fun handle(id: Long) = executor.io { index.handle(id) }

    suspend fun scan(feature: CleanupFeature, progress: (ScanProgress) -> Unit): ScanHandle =
        scanLock.withLock {
            executor.io {
                val permission = access.resolve(feature)
                if (permission.request != AccessRequest.NONE)
                    throw SecurityException("Access required")
                val session = index.start(feature)
                try {
                    val batch = ArrayList<ScannedFile>(200)
                    val smartClean = feature == CleanupFeature.SMART_CLEAN
                    var junkBytes = 0L
                    val startedAt = System.currentTimeMillis()
                    val count =
                        sources.scan(
                            permission,
                            session,
                            { file, folder ->
                                if (CleanupPolicy.candidate(feature, file, folder, startedAt)) {
                                    batch +=
                                        if (feature == CleanupFeature.SMART_CLEAN)
                                            file.copy(
                                                bucket =
                                                    com.example.aicleanphonestorage.feature
                                                        .junkcleaner
                                                        .data
                                                        .JunkRules
                                                        .classify(file, startedAt, folder)
                                                        ?.name
                                                        .orEmpty()
                                            )
                                        else file
                                    if (batch.size == 200) {
                                        junkBytes += index.insert(session, batch)
                                        batch.clear()
                                    }
                                }
                            },
                            includeEmptyDirectories = smartClean,
                        ) { done, total ->
                            progress(ScanProgress(done, total, junkBytes = junkBytes.takeIf { smartClean }))
                        }
                    currentCoroutineContext().ensureActive()
                    if (batch.isNotEmpty()) junkBytes += index.insert(session, batch)
                    val label =
                        when (permission.source) {
                            ScanSourceKind.MEDIA ->
                                if (permission.limited) "Selected photos" else "Photos"
                            ScanSourceKind.DIRECT -> "Shared storage"
                            ScanSourceKind.DOCUMENT -> "Selected folder"
                            null -> ""
                        }
                    ScanHandle(session, feature, count, label, permission.limited)
                        .also { handle ->
                            index.finishScan(handle)
                            if (smartClean) {
                                val categories = junkIndex.visibleCategories(session)
                                progress(ScanProgress(count, count, "FILES", categories.sumOf { it.bytes }))
                                telemetry.junkScan(categories)
                            } else telemetry.scan(feature, index.totals(handle, CleanupFilter(minimumBytes = 0)))
                        }
                } catch (error: Exception) {
                    // 取消或失败只移除本应用的临时索引，绝不触碰原文件。
                    withContext(NonCancellable) { index.discard(session) }
                    throw error
                }
            }
        }

    fun pager(handle: ScanHandle, filter: CleanupFilter) =
        Pager(
            PagingConfig(
                pageSize = 60,
                initialLoadSize = 120,
                prefetchDistance = 12,
                maxSize = 240,
                enablePlaceholders = false,
            )
        ) {
            FilePageSource(index, executor, handle, filter)
        }

    suspend fun totals(handle: ScanHandle, filter: CleanupFilter) =
        executor.io { index.totals(handle, filter) }

    suspend fun select(id: Long, selected: Boolean) = executor.io { index.select(id, selected) }

    suspend fun selectAll(handle: ScanHandle, filter: CleanupFilter, selected: Boolean) =
        executor.io { index.selectAll(handle, filter, selected) }

    suspend fun quality(id: Long, quality: Int) =
        executor.io { index.quality(id, quality.coerceIn(40, 95)) }

    suspend fun prepare(handle: ScanHandle, filter: CleanupFilter) =
        executor.io {
            val id = index.prepareOperation(handle, filter)
            PreparedOperation(id, index.operationCount(id), index.operationBytes(id))
        }
}

private class FilePageSource(
    private val index: ScanIndex,
    private val executor: TaskExecutor,
    private val handle: ScanHandle,
    private val filter: CleanupFilter,
) : PagingSource<Int, ScannedFile>() {
    init {
        index.register(this)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ScannedFile> =
        try {
            val offset = params.key ?: 0
            val rows = executor.io { index.page(handle, filter, offset, params.loadSize) }
            LoadResult.Page(
                rows,
                if (offset == 0) null else (offset - 60).coerceAtLeast(0),
                if (rows.size < params.loadSize) null else offset + rows.size,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }

    override fun getRefreshKey(state: PagingState<Int, ScannedFile>): Int? =
        state.anchorPosition?.let {
            ((it - state.config.initialLoadSize / 2).coerceAtLeast(0) / state.config.pageSize) *
                state.config.pageSize
        }
}
