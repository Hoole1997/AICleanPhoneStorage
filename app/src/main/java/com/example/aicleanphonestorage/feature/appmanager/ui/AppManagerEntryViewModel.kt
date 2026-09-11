package com.example.aicleanphonestorage.feature.appmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.appmanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface AppManagerEntryState {
    data object Idle : AppManagerEntryState

    data class Loading(val id: Long, val frame: TimedEntryProgress.Frame? = null) :
        AppManagerEntryState

    data class Ready(val catalog: AppManagerCatalog) : AppManagerEntryState

    data object Failed : AppManagerEntryState
}

/** 首页独立入口：旋转保留任务，取消/后台后不再跳转；进程死亡后不隐式恢复广告等待。 */
internal class AppManagerEntryViewModel(
    private val repository: AppManagerRepository,
    private val loader: TimedEntryLoader = TimedEntryLoader(),
) : ViewModel() {
    private val current = MutableStateFlow<AppManagerEntryState>(AppManagerEntryState.Idle)
    val state = current.asStateFlow()
    private var work: Job? = null
    private var sequence = 0L

    fun begin() {
        if (
            current.value is AppManagerEntryState.Loading ||
                current.value is AppManagerEntryState.Ready
        )
            return
        val id = ++sequence
        current.value = AppManagerEntryState.Loading(id)
        work =
            viewModelScope.launch {
                try {
                    val result =
                        loader.load(
                            count = { it: AppManagerCatalog -> it.apps.size },
                            onStalled = { failIfCurrent(id) },
                            onFrame = { frame ->
                                if ((current.value as? AppManagerEntryState.Loading)?.id == id)
                                    current.value = AppManagerEntryState.Loading(id, frame)
                            },
                        ) { report ->
                            repository.load { done, total ->
                                report(TaskProgress("APPLICATIONS", done, total))
                            }
                        }
                    currentCoroutineContext().ensureActive()
                    if ((current.value as? AppManagerEntryState.Loading)?.id == id)
                        current.value = AppManagerEntryState.Ready(result)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failIfCurrent(id)
                } finally {
                    // 自取消、内部异常等出口也必须结束仍属于本次请求的 Loading。
                    failIfCurrent(id)
                }
            }
    }

    private fun failIfCurrent(id: Long) {
        if ((current.value as? AppManagerEntryState.Loading)?.id == id)
            current.value = AppManagerEntryState.Failed
    }

    fun cancel() {
        work?.cancel()
        work = null
        current.value = AppManagerEntryState.Idle
    }

    fun cancel(id: Long) {
        if ((current.value as? AppManagerEntryState.Loading)?.id == id) cancel()
    }

    fun consume(): AppManagerCatalog? {
        val result = (current.value as? AppManagerEntryState.Ready)?.catalog ?: return null
        current.value = AppManagerEntryState.Idle
        return result
    }
}
