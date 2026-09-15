package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.docview.push.NotificationDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class StartupEntry(
    val destination: NotificationDestination,
    val previewMode: String? = null,
    val hotStart: Boolean = false,
    val notificationOrigin: String? = null,
)

internal data class StartupState(
    val prepared: Boolean = false,
    val permissionCompleted: Boolean = false,
    val adCompleted: Boolean = false,
    val minimumStayComplete: Boolean = false,
    val consumed: Boolean = false,
) {
    val ready: Boolean
        get() = prepared && permissionCompleted && adCompleted && minimumStayComplete
}

/** 权限结束且启动页可见后才开始广告阶段；最短停留从广告请求开始计算，不包含系统授权页耗时。 */
internal class StartupViewModel(
    private val saved: SavedStateHandle,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    prepare: suspend () -> Unit,
) : ViewModel() {
    // beginAd 只在前台有焦点并完成首帧后调用；旋转保留起点，进程重建重新开始。
    private var adStartedAt: Long? = null
    private var minimumStayJob: Job? = null
    private var sequence = 0L
    private var requestId: Long? = null
    private var cleared = false
    private val current = MutableStateFlow(StartupState(consumed = saved[CONSUMED] ?: false))
    val state = current.asStateFlow()

    init {
        // 应用级语言恢复已自行限时；广告等待和旋转不取消这次准备，不在这里截断 SDK 请求。
        viewModelScope.launch {
            try {
                prepare()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                /* 准备失败保留系统默认，仍按正常广告回调流程进入首页。 */
            }
            current.value = current.value.copy(prepared = true)
        }
    }

    fun accept(entry: StartupEntry) {
        saved[DESTINATION] = entry.destination.key
        saved[PREVIEW] = entry.previewMode
        saved[HOT_START] = entry.hotStart
        saved["startup.notification.origin"] = entry.notificationOrigin
        // 等待中的新通知只更新目标；已交接后的新入口才开启下一次广告请求。
        if (current.value.consumed) {
            minimumStayJob?.cancel()
            adStartedAt = null
            saved[CONSUMED] = false
            requestId = null
            current.value =
                StartupState(
                    prepared = current.value.prepared,
                    permissionCompleted = current.value.permissionCompleted,
                )
        }
    }

    fun hasEntry() = saved.contains(DESTINATION)

    fun entry() = StartupEntry(NotificationDestination.fromKey(saved[DESTINATION]), saved[PREVIEW], saved[HOT_START] ?: false, saved["startup.notification.origin"])

    fun permissionFinished() {
        if (!cleared) current.value = current.value.copy(permissionCompleted = true)
    }

    fun canRequestAd() =
        !cleared &&
            current.value.prepared &&
            current.value.permissionCompleted &&
            !current.value.consumed &&
            !current.value.adCompleted &&
            requestId == null

    fun beginAd(): Long? {
        if (!canRequestAd()) return null
        adStartedAt = clock()
        return (++sequence).also { requestId = it }
    }

    fun adFinished(id: Long) {
        if (cleared || requestId != id || current.value.consumed) return
        val startedAt = adStartedAt ?: return
        requestId = null
        // SDK 立即回调时仍展示启动页至少 3 秒；真实广告已经耗时足够则无需再等。
        val remaining = (MINIMUM_STAY_MS - (clock() - startedAt).coerceAtLeast(0)).coerceAtLeast(0)
        current.value =
            current.value.copy(adCompleted = true, minimumStayComplete = remaining == 0L)
        if (remaining > 0) {
            // 单次延迟，无轮询；等待期间循环进度继续，不干预 SDK 本身的广告展示。
            minimumStayJob =
                viewModelScope.launch {
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
        adStartedAt = null
        minimumStayJob?.cancel()
    }

    private companion object {
        const val MINIMUM_STAY_MS = 3_000L
        const val DESTINATION = "startup.destination"
        const val PREVIEW = "startup.preview"
        const val HOT_START = "startup.hot"
        const val CONSUMED = "startup.consumed"
    }
}
