package com.example.aicleanphonestorage.feature.appmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.appmanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class AppManagerUiState(
    val catalog: AppManagerCatalog? = null,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

/** 返回设置后重新读取元数据；刷新期间保留旧列表，由 ListAdapter 做 Diff，不使用页面内广告弹框。 */
internal class AppManagerViewModel(
    private val repository: AppManagerRepository,
    initial: AppManagerCatalog?,
) : ViewModel() {
    private val current = MutableStateFlow(AppManagerUiState(catalog = initial))
    val state = current.asStateFlow()
    private var work: Job? = null
    private var needsRefresh = initial == null

    fun onForeground() {
        if (needsRefresh) {
            needsRefresh = false
            refresh()
        }
    }

    fun onBackground() {
        needsRefresh = true
        work?.cancel()
        work = null
        current.update { it.copy(refreshing = false) }
    }

    fun refresh() {
        if (work?.isActive == true) return
        current.update { it.copy(refreshing = true, failed = false) }
        work =
            viewModelScope.launch {
                try {
                    val catalog = repository.load()
                    currentCoroutineContext().ensureActive()
                    current.value = AppManagerUiState(catalog = catalog)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    current.update { it.copy(refreshing = false, failed = true) }
                }
            }
    }
}
