package com.example.aicleanphonestorage.app.ad

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PendingInterstitialAction(val id: Long, val action: String, val ready: Boolean = false, val payload: Long = 0)

/** 只保留动作名和请求编号，不持有 Activity/回调；旋转保留，进程重启不自动重放用户操作。 */
internal class InterstitialActionState : ViewModel() {
    private var sequence = 0L
    private var cleared = false
    private val current = MutableStateFlow<PendingInterstitialAction?>(null)
    val pending = current.asStateFlow()

    fun begin(action: String, payload: Long = 0): Long? {
        if (cleared || current.value != null) return null
        val id = ++sequence
        current.value = PendingInterstitialAction(id, action, payload = payload)
        return id
    }

    fun complete(id: Long) {
        if (cleared) return
        val value = current.value?.takeIf { it.id == id } ?: return
        current.value = value.copy(ready = true)
    }

    fun consume(): String? {
        val value = current.value?.takeIf { it.ready } ?: return null
        current.value = null
        return value.action
    }

    override fun onCleared() {
        cleared = true
        current.value = null
    }
}
