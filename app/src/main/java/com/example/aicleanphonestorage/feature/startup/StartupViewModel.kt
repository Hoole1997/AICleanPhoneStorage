package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.docview.push.NotificationDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StartupEntry(val destination: NotificationDestination, val previewMode: String? = null)
internal data class StartupState(
    val prepared: Boolean = false,
    val adCompleted: Boolean = false,
    val minimumStayComplete: Boolean = false,
    val consumed: Boolean = false,
) {
    val ready: Boolean get() = prepared && adCompleted && minimumStayComplete
}

/** 广告回调与页面最短停留共同放行；等待按进入页面计算，不从 call 回调重新计时。 */
internal class StartupViewModel(
    private val saved: SavedStateHandle,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    prepare: suspend () -> Unit,
) : ViewModel() {
    // ViewModel 在旋转时保留；进程重建重新进入页面，重新计算最短停留。
    private var enteredAt = clock()
    private var minimumStayJob: Job? = null
    private var sequence = 0L
    private var requestId: Long? = null
    private var cleared = false
    private val current = MutableStateFlow(StartupState(consumed = saved[CONSUMED] ?: false))
    val state = current.asStateFlow()

    init {
        // 应用级语言恢复已自行限时；广告等待和旋转不取消这次准备，不在这里截断 SDK 请求。
        viewModelScope.launch {
            try { prepare() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 准备失败保留系统默认，仍按正常广告回调流程进入首页。 */ }
            current.value = current.value.copy(prepared = true)
        }
    }

    fun accept(entry: StartupEntry) {
        saved[DESTINATION] = entry.destination.key
        saved[PREVIEW] = entry.previewMode
        // 等待中的新通知只更新目标；已交接后的新入口才开启下一次广告请求。
        if (current.value.consumed) {
            minimumStayJob?.cancel()
            enteredAt = clock()
            saved[CONSUMED] = false
            requestId = null
            current.value = StartupState(prepared = current.value.prepared)
        }
    }

    fun hasEntry() = saved.contains(DESTINATION)
    fun entry() = StartupEntry(NotificationDestination.fromKey(saved[DESTINATION]), saved[PREVIEW])

    fun canRequestAd() = !cleared && current.value.prepared &&
        !current.value.consumed && !current.value.adCompleted && requestId == null

    fun beginAd(): Long? {
        if (!canRequestAd()) return null
        return (++sequence).also { requestId = it }
    }

    fun adFinished(id: Long) {
        if (cleared || requestId != id || current.value.consumed) return
        requestId = null
        val remaining = (MINIMUM_STAY_MS - (clock() - enteredAt).coerceAtLeast(0)).coerceAtLeast(0)
        current.value = current.value.copy(adCompleted = true, minimumStayComplete = remaining == 0L)
        if (remaining > 0) {
            // 单次延迟，无轮询；等待期间循环进度继续，不干预 SDK 本身的广告展示。
            minimumStayJob = viewModelScope.launch {
                delay(remaining)
                current.value = current.value.copy(minimumStayComplete = true)
            }
        }
    }

    fun consume(): StartupEntry? {
        if (cleared || !current.value.ready || current.value.consumed) return null
        saved[CONSUMED] = true
        current.value = current.value.copy(consumed = true)
        return entry()
    }

    override fun onCleared() {
        cleared = true
        requestId = null
        minimumStayJob?.cancel()
    }

    private companion object {
        const val MINIMUM_STAY_MS = 3_000L
        const val DESTINATION = "startup.destination"
        const val PREVIEW = "startup.preview"
        const val CONSUMED = "startup.consumed"
    }
}
