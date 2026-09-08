package com.example.aicleanphonestorage.core.permissions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PermissionPhase {
    CHECKING,
    LAUNCH,
    WAITING,
    EXPIRED,
    RESULT,
}

internal data class PermissionRequest(
    val id: String,
    val route: String,
    val kind: PermissionKind,
    val settings: Boolean,
    val started: Long,
    val phase: PermissionPhase = PermissionPhase.CHECKING,
    val leftHost: Boolean = false,
    val returnAttempted: Boolean = false,
    val granted: Boolean = false,
    val directory: String? = null,
    val unavailable: Boolean = false,
)

/** 用户明确打开授权页期间才允许跨 onStop 检测，最多两分钟；无 Service/WakeLock/全局协程。 */
internal class PermissionFlowViewModel(
    private val access: PermissionAccess,
    private val saved: SavedStateHandle,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val timeout: Long = 120_000L,
) : ViewModel() {
    private val current = MutableStateFlow(restore())
    val state = current.asStateFlow()
    private var job: Job? = null
    val pending: Boolean
        get() = current.value != null

    init {
        val restored = current.value
        if (restored != null && restored.phase == PermissionPhase.WAITING) watch(restored)
    }

    fun requestedRuntime(kind: PermissionKind) =
        saved.get<Boolean>("permission.asked.${kind.name}") == true

    fun begin(route: String, kind: PermissionKind, settings: Boolean = kind.special) {
        cancel()
        val request =
            PermissionRequest(UUID.randomUUID().toString(), route, kind, settings, clock())
        set(request)
        job =
            viewModelScope.launch {
                try {
                    val granted = kind != PermissionKind.DIRECTORY && access.granted(kind)
                    currentCoroutineContext().ensureActive()
                    if (granted) finish(request, true)
                    else if (current.value?.id == request.id)
                        set(request.copy(phase = PermissionPhase.LAUNCH))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    finish(request, false, unavailable = true)
                }
            }
    }

    fun launched(id: String): Boolean {
        val request =
            current.value?.takeIf { it.id == id && it.phase == PermissionPhase.LAUNCH }
                ?: return false
        if (clock() - request.started !in 0 until timeout) {
            finish(request, false)
            return false
        }
        val contract =
            if (request.kind == PermissionKind.DIRECTORY) "permission.tree.contract"
            else if (!request.settings) "permission.runtime.contract" else null
        if (contract != null) {
            if (saved.get<String>(contract) != null) {
                finish(request, false, unavailable = true)
                return false
            }
            saved[contract] = id
        }
        if (!request.settings && request.kind != PermissionKind.DIRECTORY)
            saved["permission.asked.${request.kind.name}"] = true
        val waiting = request.copy(phase = PermissionPhase.WAITING)
        set(waiting)
        watch(waiting)
        return true
    }

    private fun watch(request: PermissionRequest) {
        job?.cancel()
        job =
            viewModelScope.launch {
                try {
                    while (
                        current.value?.id == request.id &&
                            current.value?.phase == PermissionPhase.WAITING
                    ) {
                        val elapsed = clock() - request.started
                        if (elapsed !in 0 until timeout) {
                            set(current.value!!.copy(phase = PermissionPhase.EXPIRED))
                            break
                        }
                        if (request.settings) {
                            val granted = access.granted(request.kind)
                            currentCoroutineContext().ensureActive()
                            if (granted) {
                                finish(request, true)
                                break
                            }
                        }
                        // 普通权限/目录由 Activity Result 返回，等待时不轮询；特殊设置首段 750ms，后续 1500ms。
                        val remaining = (timeout - (clock() - request.started)).coerceAtLeast(1)
                        val interval = if (elapsed < 15_000) 750L else 1500L
                        delay(if (request.settings) minOf(interval, remaining) else remaining)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (current.value?.id == request.id)
                        set(current.value!!.copy(phase = PermissionPhase.EXPIRED))
                }
            }
    }

    fun leftHost() {
        current.value
            ?.takeIf { it.phase == PermissionPhase.WAITING }
            ?.let { set(it.copy(leftHost = true)) }
    }

    fun resumed() {
        val request = current.value ?: return
        if (
            request.phase == PermissionPhase.RESULT ||
                request.phase == PermissionPhase.LAUNCH ||
                request.phase == PermissionPhase.CHECKING
        )
            return
        if (!request.leftHost && request.phase != PermissionPhase.EXPIRED) return
        if (request.kind == PermissionKind.DIRECTORY) {
            job?.cancel()
            if (saved.get<String>("permission.tree.contract") == request.id)
                saved.remove<String>("permission.tree.contract")
            finish(request, false)
            return
        }
        checkResult(request)
    }

    fun runtimeResult() {
        val id = saved.remove<String>("permission.runtime.contract") ?: return
        current.value
            ?.takeIf { it.id == id && !it.settings && it.kind != PermissionKind.DIRECTORY }
            ?.let(::checkResult)
    }

    private fun checkResult(request: PermissionRequest) {
        job?.cancel()
        set(request.copy(phase = PermissionPhase.CHECKING))
        job =
            viewModelScope.launch {
                try {
                    val granted = access.granted(request.kind)
                    currentCoroutineContext().ensureActive()
                    finish(request, granted)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    finish(request, false, unavailable = true)
                }
            }
    }

    fun directoryResult(uri: String?) {
        val id = saved.remove<String>("permission.tree.contract") ?: return
        val request =
            current.value?.takeIf { it.id == id && it.kind == PermissionKind.DIRECTORY } ?: return
        job?.cancel()
        if (uri == null) {
            finish(request, false)
            return
        }
        set(request.copy(phase = PermissionPhase.CHECKING))
        job =
            viewModelScope.launch {
                try {
                    access.persistDirectory(uri)
                    currentCoroutineContext().ensureActive()
                    finish(request, true, directory = uri)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    finish(request, false, unavailable = true)
                }
            }
    }

    fun launchFailed(id: String) {
        val request =
            current.value?.takeIf { it.id == id && it.phase != PermissionPhase.RESULT } ?: return
        job?.cancel()
        for (key in listOf("permission.runtime.contract", "permission.tree.contract")) if (
            saved.get<String>(key) == id
        )
            saved.remove<String>(key)
        finish(request, false, unavailable = true)
    }

    fun markReturnAttempted(id: String): Boolean {
        val value =
            current.value?.takeIf {
                it.id == id && it.phase == PermissionPhase.RESULT && !it.returnAttempted
            } ?: return false
        set(value.copy(returnAttempted = true))
        return true
    }

    fun consume(): PermissionRequest? {
        val value = current.value?.takeIf { it.phase == PermissionPhase.RESULT } ?: return null
        cancel()
        return value
    }

    fun cancel() {
        job?.cancel()
        job = null
        set(null)
    }

    private fun finish(
        request: PermissionRequest,
        granted: Boolean,
        directory: String? = null,
        unavailable: Boolean = false,
    ) {
        val value = current.value?.takeIf { it.id == request.id } ?: return
        set(
            value.copy(
                phase = PermissionPhase.RESULT,
                granted = granted,
                directory = directory,
                unavailable = unavailable,
            )
        )
    }

    private fun set(value: PermissionRequest?) {
        current.value = value
        if (value == null) {
            saved.remove<String>(KEY)
            return
        }
        saved["permission.route"] = value.route
        saved["permission.kind"] = value.kind.name
        saved["permission.settings"] = value.settings
        saved["permission.started"] = value.started
        saved["permission.phase"] = value.phase.name
        saved["permission.left"] = value.leftHost
        saved["permission.returned"] = value.returnAttempted
        saved["permission.granted"] = value.granted
        saved["permission.directory"] = value.directory
        saved["permission.unavailable"] = value.unavailable
        saved[KEY] = value.id
    }

    private fun restore(): PermissionRequest? {
        val id = saved.get<String>(KEY) ?: return null
        return try {
            val phase =
                PermissionPhase.valueOf(saved.get<String>("permission.phase") ?: return null)
            PermissionRequest(
                id,
                saved["permission.route"] ?: return null,
                PermissionKind.valueOf(saved["permission.kind"] ?: return null),
                saved["permission.settings"] ?: false,
                saved["permission.started"] ?: 0L,
                if (phase == PermissionPhase.CHECKING) PermissionPhase.EXPIRED else phase,
                saved["permission.left"] ?: false,
                saved["permission.returned"] ?: false,
                saved["permission.granted"] ?: false,
                saved["permission.directory"],
                saved["permission.unavailable"] ?: false,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val KEY = "permission.pending"
    }
}
