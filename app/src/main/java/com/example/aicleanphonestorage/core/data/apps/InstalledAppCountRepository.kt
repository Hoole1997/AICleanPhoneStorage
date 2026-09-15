package com.example.aicleanphonestorage.core.data.apps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 首页和常驻通知共享同一数值快照；不在 StateFlow 中保存应用列表，null 不冒充 0。 */
internal class InstalledAppCountRepository(private val readCount: suspend () -> Int) {
    private val lock = Mutex()
    private val current = MutableStateFlow<Int?>(null)
    val count = current.asStateFlow()
    @Volatile private var revision = 0L

    suspend fun refresh(): Int {
        val requestedRevision = revision
        return lock.withLock {
            // 首页和前台恢复同时请求时复用刚完成的读取，不为两个显示位置分别查询。
            if (revision != requestedRevision) current.value?.let { return@withLock it }
            try {
                val value = readCount()
                currentCoroutineContext().ensureActive()
                require(value >= 0)
                current.value = value
                revision++
                value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                current.value = null
                throw error
            }
        }
    }
}
