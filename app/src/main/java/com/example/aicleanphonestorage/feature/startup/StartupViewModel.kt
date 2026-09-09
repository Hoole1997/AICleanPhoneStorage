package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.docview.push.NotificationDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StartupEntry(val destination: NotificationDestination, val previewMode: String? = null)
internal data class StartupState(val ready: Boolean = false, val consumed: Boolean = false)

/** 只保存小型路由和单调时钟。后台无动画/轮询；旋转和新通知不会无限延长同一次启动等待。 */
internal class StartupViewModel(
    private val saved: SavedStateHandle,
    private val prepare: suspend () -> Unit,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : ViewModel() {
    private var started = saved.get<Long>(START)?.takeIf { it <= clock() } ?: clock()
    private var job: Job? = null
    private val current = MutableStateFlow(StartupState(consumed = saved[CONSUMED] ?: false))
    val state = current.asStateFlow()

    init { saved[START] = started }

    fun accept(entry: StartupEntry) {
        saved[DESTINATION] = entry.destination.key
        saved[PREVIEW] = entry.previewMode
        // 当前启动页已完成交接时，新点击才开始新的启动周期。
        if (current.value.consumed) {
            started = clock()
            saved[START] = started
            saved[CONSUMED] = false
            current.value = StartupState()
        }
    }

    fun hasEntry() = saved.contains(DESTINATION)
    fun entry() = StartupEntry(NotificationDestination.fromKey(saved[DESTINATION]), saved[PREVIEW])
    fun elapsed() = (clock() - started).coerceAtLeast(0)
    fun progress(): Int = if (current.value.ready) 1000 else
        ((elapsed().toFloat() / MINIMUM_MS) * 900).toInt().coerceIn(0, 900)

    fun resumed() {
        if (current.value.ready || current.value.consumed || job?.isActive == true) return
        job = viewModelScope.launch {
            val remaining = (MAXIMUM_MS - elapsed()).coerceAtLeast(0)
            if (remaining > 0) {
                withTimeoutOrNull(remaining) {
                    coroutineScope {
                        val preparation = async {
                            try { prepare() }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { /* 启动准备失败仍允许进入首页，业务页面自行显示状态。 */ }
                        }
                        delay((MINIMUM_MS - elapsed()).coerceAtLeast(0))
                        preparation.await()
                    }
                }
            }
            current.value = current.value.copy(ready = true)
        }
    }

    fun paused() { job?.cancel(); job = null }

    fun consume(): StartupEntry? {
        if (!current.value.ready || current.value.consumed) return null
        saved[CONSUMED] = true
        current.value = current.value.copy(consumed = true)
        return entry()
    }

    companion object {
        const val MINIMUM_MS = 3_000L
        const val MAXIMUM_MS = 15_000L
        private const val START = "startup.started"
        private const val DESTINATION = "startup.destination"
        private const val PREVIEW = "startup.preview"
        private const val CONSUMED = "startup.consumed"
    }
}
