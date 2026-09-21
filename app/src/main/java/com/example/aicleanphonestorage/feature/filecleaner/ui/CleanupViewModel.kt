package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface CleanupOperationState {
    data object Idle : CleanupOperationState

    data class Confirm(val id: Long, val count: Int, val bytes: Long) :
        CleanupOperationState

    /** 用户点击压缩即授权创建副本；等待既有广告流程结束后按冻结的操作 ID 启动。 */
    data class CompressionReady(val id: Long) : CleanupOperationState
    data class EmptyJunkReady(val id: Long) : CleanupOperationState

    data class Running(val id: Long, val done: Int = 0, val total: Int = 0) : CleanupOperationState

    data class Consent(val id: Long, val sender: IntentSender) : CleanupOperationState

    data class Result(val id: Long, val summary: OperationSummary) : CleanupOperationState
}

internal data class CleanupUiState(
    val handle: ScanHandle? = null,
    val filter: CleanupFilter = CleanupFilter(),
    val totals: SelectionTotals = SelectionTotals(),
    val operation: CleanupOperationState = CleanupOperationState.Idle,
    val editing: Int = 0,
    val error: Long = 0,
    val totalsReady: Boolean = false,
    val refreshing: Boolean = false,
    val unusedGroups: List<com.example.aicleanphonestorage.feature.unused.data.UnusedGroup> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class CleanupViewModel(
    private val repository: FileScanRepository,
    private val operations: FileOperationEngine,
    private val saved: SavedStateHandle,
    scanId: Long,
    initialFilter: CleanupFilter = CleanupFilter(),
    private val telemetry: com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry = com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry(),
) : ViewModel() {
    private val restoredFilter =
        CleanupFilter(
            bucket = saved["filter.bucket"] ?: initialFilter.bucket,
            category =
                FileCategory.entries.getOrElse(saved["filter.category"] ?: 0) { FileCategory.ALL },
            minimumBytes = saved["filter.size"] ?: 10_000_000L,
            recentDays = saved["filter.recent"] ?: 0,
            unusedDays = 30, // 固定候选范围，旧版本保存的筛选条件不再隐式过滤结果。
            referenceMillis = saved["filter.clock"] ?: System.currentTimeMillis(),
        )
    private val current = MutableStateFlow(CleanupUiState(filter = restoredFilter))
    // 存储被撤销、磁盘已满等可恢复错误统一转换为 UI 状态；取消仍由父 Job 传播。
    private val failures = CoroutineExceptionHandler { _, error ->
        when (error) {
            is java.io.IOException,
            is android.database.SQLException,
            is SecurityException ->
                current.update {
                    it.copy(operation = CleanupOperationState.Idle, error = it.error + 1)
                }
            else -> throw error
        }
    }
    val state = current.asStateFlow()
    private val query = MutableStateFlow<Pair<ScanHandle, CleanupFilter>?>(null)
    val files =
        query
            .filterNotNull()
            .flatMapLatest { (handle, filter) -> repository.pager(handle, filter).flow }
            .cachedIn(viewModelScope)
    val videoRows = query.filterNotNull().filter { it.first.feature == CleanupFeature.VIDEOS }
        .flatMapLatest { repository.videos.pager(it.first.id).flow }.cachedIn(viewModelScope)
    private val similarColumns = MutableStateFlow(3)
    val similarRows = combine(query.filterNotNull(), similarColumns) { q, columns -> q.first to columns }
        .filter { it.first.feature == CleanupFeature.SIMILAR_PHOTOS }
        .flatMapLatest { (handle, columns) -> repository.similarPaging.pager(handle.id, columns).flow }.cachedIn(viewModelScope)
    private var similarRefreshPending = false
    private var similarRefresh: Job? = null
    private var videoRefresh: Job? = null
    private var videoRefreshPending = false
    private var work: Job? = null
    private var totalsJob: Job? = null

    init {
        val restoredScan = saved.get<Long>(SCAN) ?: scanId
        saved[SCAN] = restoredScan
        viewModelScope.launch(failures) {
            val handle = repository.handle(restoredScan)
            current.update { it.copy(handle = handle, error = if (handle == null) 1 else 0) }
            handle?.let {
                query.value = it to current.value.filter
                refreshTotals()
                onVideoForeground()
                onSimilarForeground()
            }
            // 进程重建只恢复结果；不会自动重启压缩或跳过删除确认。
            saved.get<Long>(OP)?.let { id ->
                if (!waitingSystem) {
                    operations.cancel(id)
                    val summary = operations.summary(id)
                    current.update {
                        it.copy(operation = CleanupOperationState.Result(id, summary))
                    }
                }
            }
        }
    }

    fun setFilter(filter: CleanupFilter) {
        if (
            current.value.operation != CleanupOperationState.Idle ||
                work?.isActive == true ||
                current.value.editing > 0
        )
            return
        if (filter == current.value.filter) return
        if (current.value.handle?.feature == CleanupFeature.LARGE_FILES) telemetry.filter(filter)
        saved["filter.bucket"] = filter.bucket
        saved["filter.category"] = filter.category.ordinal
        saved["filter.size"] = filter.minimumBytes
        saved["filter.recent"] = filter.recentDays
        saved["filter.unused"] = filter.unusedDays
        saved["filter.clock"] = filter.referenceMillis
        current.update { it.copy(filter = filter) }
        current.value.handle?.let {
            query.value = it to filter
            refreshTotals()
        }
    }

    fun toggle(id: Long, selected: Boolean) = edit {
        repository.select(id, selected)
        reportSelection(selected)
    }

    fun selectAll() = edit {
        current.value.handle?.let {
            val selected = current.value.totals.selectedCount != current.value.totals.count
            repository.selectAll(it, current.value.filter, selected)
            reportSelection(selected)
        }
    }

    fun selectBucket(bucket: String, selected: Boolean) = edit {
        current.value.handle?.let {
            repository.selectAll(it, current.value.filter.copy(bucket = bucket), selected)
            if (it.feature == CleanupFeature.UNUSED_FILES)
                telemetry.selection(it.feature, bucket, selected, repository.totals(it, current.value.filter.copy(bucket = bucket)))
            else if (it.feature == CleanupFeature.VIDEOS || it.feature == CleanupFeature.SIMILAR_PHOTOS)
                // 分组操作上报整个页面的已选大小，与底部 Clean 按钮保持一致。
                reportSelection(selected)
        }
    }

    private suspend fun reportSelection(selected: Boolean) {
        val value = current.value
        val handle = value.handle ?: return
        if (handle.feature == CleanupFeature.UNUSED_FILES && value.filter.bucket == null) {
            repository.unusedGroups(handle).filter { it.totals.count > 0 }.forEach {
                telemetry.selection(handle.feature, it.kind.bucket, selected, it.totals)
            }
            return
        }
        val totals = repository.totals(handle, value.filter)
        telemetry.selection(handle.feature, value.filter.bucket, selected, totals)
    }

    fun collapseVideoMonth(month: String, collapsed: Boolean) = edit {
        current.value.handle?.takeIf { it.feature == CleanupFeature.VIDEOS }?.let {
            repository.videos.collapse(it.id, month, collapsed)
        }
    }

    fun refreshVideos() {
        // 完成页可能比索引恢复更早返回，保留刷新意图，初始化完成后再执行。
        videoRefreshPending = true
        val handle = current.value.handle?.takeIf { it.feature == CleanupFeature.VIDEOS } ?: return
        if (current.value.operation != CleanupOperationState.Idle || videoRefresh?.isActive == true) return
        current.update { it.copy(editing = it.editing + 1) }
        videoRefresh = viewModelScope.launch {
            try {
                repository.refreshVideos(handle)
                videoRefreshPending = false
                refreshTotals()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { current.update { it.copy(error = it.error + 1) } }
            finally { current.update { it.copy(editing = (it.editing - 1).coerceAtLeast(0)) } }
        }
    }

    fun onVideoForeground() {
        if (videoRefreshPending && videoRefresh?.isActive != true) refreshVideos()
    }

    fun setSimilarColumns(columns: Int) { similarColumns.value = columns.coerceIn(2, 3) }
    fun makeOriginal(id: Long) = edit {
        current.value.handle?.takeIf { it.feature == CleanupFeature.SIMILAR_PHOTOS }?.let { repository.original(it, id) }
    }
    fun photoUnavailable(id: Long) {
        viewModelScope.launch(failures) { repository.unavailable(id); refreshTotals() }
    }
    fun refreshSimilar() {
        similarRefreshPending = true
        if (current.value.handle?.feature != CleanupFeature.SIMILAR_PHOTOS ||
            current.value.operation != CleanupOperationState.Idle || similarRefresh?.isActive == true) return
        current.update { it.copy(editing = it.editing + 1, refreshing = true) }
        similarRefresh = viewModelScope.launch(failures) {
            try {
                val handle = repository.scan(CleanupFeature.SIMILAR_PHOTOS) { }
                ensureActive()
                saved[SCAN] = handle.id
                current.update { it.copy(handle = handle, totalsReady = false) }
                query.value = handle to current.value.filter
                similarRefreshPending = false
                refreshTotals()
            } finally { current.update { it.copy(editing = (it.editing - 1).coerceAtLeast(0), refreshing = false) } }
        }
    }
    fun onSimilarForeground() {
        if (similarRefreshPending && similarRefresh?.isActive != true) refreshSimilar()
    }

    fun quality(id: Long, quality: Int) = edit { repository.quality(id, quality) }

    private fun edit(block: suspend () -> Unit) {
        if (
            current.value.operation != CleanupOperationState.Idle ||
                work?.isActive == true ||
                current.value.editing > 0
        )
            return
        current.update { it.copy(editing = it.editing + 1) }
        viewModelScope.launch(failures) {
            try {
                block()
                refreshTotals()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                current.update { it.copy(error = it.error + 1) }
            } finally {
                current.update { it.copy(editing = it.editing - 1) }
            }
        }
    }

    fun refreshTotals() {
        totalsJob?.cancel()
        val value = current.value
        val handle = value.handle ?: return
        totalsJob =
            viewModelScope.launch(failures) {
                val totals = repository.totals(handle, value.filter)
                val groups = if (handle.feature == CleanupFeature.UNUSED_FILES && value.filter.bucket == null)
                    repository.unusedGroups(handle) else emptyList()
                current.update { it.copy(totals = totals, totalsReady = true, unusedGroups = groups) }
            }
    }

    fun prepare() {
        if (work?.isActive == true) return
        work = viewModelScope.launch(failures) {
            // 页面恢复可能正在刷新总览；等待当前查询完成，再冻结最新选择快照。
            while (totalsJob?.isActive == true) totalsJob?.join()
            val value = current.value
            val handle = value.handle ?: return@launch
            if (
                value.editing > 0 || (value.totals.selectedCount == 0 &&
                    !(handle.feature == CleanupFeature.SMART_CLEAN && value.totalsReady && value.totals.count == 0)) ||
                    value.operation != CleanupOperationState.Idle
            ) return@launch
            telemetry.cleanClick(handle.feature, value.totals)
            val op = repository.prepare(handle, value.filter)
            current.update {
                it.copy(
                    operation = if (handle.feature == CleanupFeature.PHOTO_COMPRESS)
                        CleanupOperationState.CompressionReady(op.id)
                    else if (handle.feature == CleanupFeature.SMART_CLEAN && op.count == 0)
                        CleanupOperationState.EmptyJunkReady(op.id)
                    else CleanupOperationState.Confirm(op.id, op.count, op.bytes)
                )
            }
        }
    }

    fun dismissOperation() {
        saved.remove<Long>(OP)
        current.update { it.copy(operation = CleanupOperationState.Idle) }
        refreshTotals()
    }

    private var reportedConfirmation: Long? = null

    fun reportConfirmation(id: Long, accepted: Boolean) {
        val value = current.value
        val op = value.operation as? CleanupOperationState.Confirm ?: return
        val feature = value.handle?.feature ?: return
        if (op.id != id || reportedConfirmation == id) return
        reportedConfirmation = id
        telemetry.confirmation(feature, accepted, op.bytes)
    }

    fun confirm(id: Long) {
        val confirmation = current.value.operation as? CleanupOperationState.Confirm ?: return
        if (confirmation.id == id) run(id, false)
    }

    fun startCompression(id: Long) {
        val ready = current.value.operation as? CleanupOperationState.CompressionReady ?: return
        // 广告重复/迟到回调不能重复压缩，也不能执行已失效的选择快照。
        if (ready.id == id) run(id, true)
    }

    fun startEmptyJunk(id: Long) {
        val ready = current.value.operation as? CleanupOperationState.EmptyJunkReady ?: return
        if (ready.id == id) run(id, false)
    }

    private fun run(id: Long, compress: Boolean) {
        saved[OP] = id
        current.update { it.copy(operation = CleanupOperationState.Running(id)) }
        work =
            viewModelScope.launch(failures) {
                try {
                    val progress: (Int, Int) -> Unit = { done, total ->
                        current.update {
                            if ((it.operation as? CleanupOperationState.Running)?.id == id)
                                it.copy(operation = CleanupOperationState.Running(id, done, total))
                            else it
                        }
                    }
                    val result =
                        if (compress) operations.compress(id, progress)
                        else operations.delete(id, progress)
                    currentCoroutineContext().ensureActive()
                    when (result) {
                        is OperationStep.Consent ->
                            current.update {
                                it.copy(
                                    operation = CleanupOperationState.Consent(id, result.sender)
                                )
                            }
                        is OperationStep.Finished ->
                            current.update {
                                it.copy(
                                    operation = CleanupOperationState.Result(id, result.summary)
                                )
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    current.update {
                        it.copy(operation = CleanupOperationState.Idle, error = it.error + 1)
                    }
                } finally {
                    refreshTotals()
                }
            }
    }

    fun consentLaunched() {
        saved[CONSENT] = true
    }

    val waitingSystem: Boolean
        get() = saved[CONSENT] ?: false

    fun consentResult(accepted: Boolean) {
        val id = saved.get<Long>(OP) ?: return
        saved[CONSENT] = false
        work =
            viewModelScope.launch(failures) {
                operations.consentResult(id, accepted)
                if (accepted) run(id, false)
                else {
                    val summary = operations.summary(id)
                    current.update {
                        if (it.handle?.feature in setOf(CleanupFeature.VIDEOS, CleanupFeature.SIMILAR_PHOTOS) && summary.deleted == 0) {
                            saved.remove<Long>(OP)
                            it.copy(operation = CleanupOperationState.Idle)
                        } else it.copy(operation = CleanupOperationState.Result(id, summary))
                    }
                    refreshTotals()
                }
            }
    }

    fun removeOriginals(id: Long) {
        val result = current.value.operation as? CleanupOperationState.Result ?: return
        if (result.id != id || result.summary.originalsAvailable <= 0) return
        // 确认后立即进入工作状态，避免索引准备期间旋转/退后台再次展示旧结果。
        current.update { it.copy(operation = CleanupOperationState.Running(id)) }
        work =
            viewModelScope.launch(failures) {
                operations.deleteOriginals(id)
                run(id, false)
            }
    }

    fun onBackground() {
        if (current.value.handle?.feature == CleanupFeature.VIDEOS && current.value.operation == CleanupOperationState.Idle) videoRefreshPending = true
        similarRefresh?.cancel()
        videoRefresh?.cancel()
        val running = current.value.operation as? CleanupOperationState.Running ?: return
        if (waitingSystem) return
        val previous = work
        previous?.cancel()
        current.update { it.copy(operation = CleanupOperationState.Idle) }
        viewModelScope.launch(failures) {
            previous?.join()
            operations.cancel(running.id)
            val summary = operations.summary(running.id)
            if (
                saved.get<Long>(OP) == running.id &&
                    current.value.operation == CleanupOperationState.Idle
            )
                current.update {
                    it.copy(operation = CleanupOperationState.Result(running.id, summary))
                }
        }
    }

    companion object {
        private const val SCAN = "cleanup.scan"
        private const val OP = "cleanup.operation"
        private const val CONSENT = "cleanup.consent"
    }
}
