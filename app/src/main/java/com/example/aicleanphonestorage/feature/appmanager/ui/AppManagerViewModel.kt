package com.example.aicleanphonestorage.feature.appmanager.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.appmanager.data.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class AppManagerUiState(
    val catalog: AppManagerCatalog? = null,
    val rows: List<ManagedApp> = emptyList(),
    val sort: AppManagerSort = AppManagerSort(),
    val refreshing: Boolean = false,
    val sorting: Boolean = false,
    val failed: Boolean = false,
)

/** 元数据和排序分别取消；刷新期间保留已显示列表，旋转/进程恢复只保存排序条件。 */
internal class AppManagerViewModel(
    private val repository: AppManagerRepository,
    initial: AppManagerCatalog?,
    private val executor: TaskExecutor,
    private val saved: SavedStateHandle,
    private val locale: Locale,
) : ViewModel() {
    private val restored =
        AppManagerSort(
            AppSortKey.entries.firstOrNull { it.name == saved.get<String>("sort.key") }
                ?: AppSortKey.LAST_USED,
            saved["sort.descending"] ?: true,
        )
    private val current = MutableStateFlow(AppManagerUiState(catalog = initial, sort = restored))
    val state = current.asStateFlow()
    private var work: Job? = null
    private var sorting: Job? = null
    private var needsRefresh = initial == null

    init {
        sortRows()
    }

    fun selectSort(key: AppSortKey) {
        val next = current.value.sort.select(key)
        saved["sort.key"] = next.key.name
        saved["sort.descending"] = next.descending
        current.update { it.copy(sort = next) }
        sortRows()
    }

    private fun sortRows() {
        sorting?.cancel()
        val catalog = current.value.catalog ?: return
        val sort = current.value.sort
        current.update { it.copy(sorting = true) }
        sorting =
            viewModelScope.launch {
                val rows =
                    executor.computation { AppManagerOrdering.sorted(catalog.apps, sort, locale) }
                ensureActive()
                current.update { it.copy(rows = rows, sorting = false) }
            }
    }

    fun onForeground() {
        if (needsRefresh) {
            needsRefresh = false
            refresh()
        }
    }

    fun onBackground() {
        needsRefresh = true
        work?.cancel()
        sorting?.cancel()
        current.update { it.copy(refreshing = false, sorting = false) }
    }

    fun refresh() {
        if (work?.isActive == true) return
        current.update { it.copy(refreshing = true, failed = false) }
        work =
            viewModelScope.launch {
                try {
                    val catalog = repository.load()
                    ensureActive()
                    current.update { it.copy(catalog = catalog, refreshing = false) }
                    sortRows()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    current.update { it.copy(refreshing = false, failed = true) }
                }
            }
    }
}
