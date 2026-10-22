package com.example.aicleanphonestorage.feature.startup.grouping

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface GroupingStore {
    fun contains(value: String): Boolean
    fun record(value: String)
}

/** 启动页触发读取，应用级串行去重；只把事件交给统一上报接口，不自行实现 Firebase/数数分发。 */
internal class GroupingReporter(
    private val read: suspend () -> String,
    private val store: GroupingStore,
    private val send: (String) -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun reportCurrent() = withContext(io) {
        mutex.withLock {
            val value = read()
            // 协议是任意非空字符串，不 trim、不截断、不把 A→B→A 当成新的 A。
            if (value.isEmpty() || store.contains(value)) return@withLock
            // SDK 接受事件和落盘是短同步临界段；页面销毁不能在两步之间取消并造成下次重复。
            withContext(NonCancellable) {
                if (send("Grouping_$value")) store.record(value)
            }
        }
    }
}
