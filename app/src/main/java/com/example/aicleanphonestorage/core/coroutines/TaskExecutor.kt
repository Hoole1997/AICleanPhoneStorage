package com.example.aicleanphonestorage.core.coroutines

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 应用共享的重任务入口；不持有 Context、线程池或独立 Scope，任务归调用方生命周期所有。
 * 信号量限制整个挂起操作的并发数，等待不会阻塞主线程，异常/取消时自动释放许可。
 *
 * 这是并发上限，不是内存/队列上限：调用方必须分批生产，禁止每个文件启动一个协程。
 * 同类任务不可嵌套调用此执行器，否则可能耗尽许可；一个顶层操作只取得一次许可。
 * CPU 循环需主动 ensureActive()；阻塞 API 需自行接入 CancellationSignal 或 runInterruptible。
 */
class TaskExecutor(
    private val dispatchers: AppDispatchers,
    ioParallelism: Int = 2,
    computationParallelism: Int = 1,
) {
    private val ioPermits = Semaphore(ioParallelism)
    private val computationPermits = Semaphore(computationParallelism)

    suspend fun <T> io(block: suspend () -> T): T = ioPermits.withPermit {
        withContext(dispatchers.io) { block() }
    }

    suspend fun <T> computation(block: suspend () -> T): T = computationPermits.withPermit {
        withContext(dispatchers.computation) { block() }
    }
}
