package com.example.aicleanphonestorage.core.coroutines

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskExecutorTest {
    @Test
    fun `io operations stay bounded even while suspended`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = TaskExecutor(AppDispatchers(dispatcher, dispatcher))
        var active = 0
        var peak = 0
        val results = (1..12).map { value ->
            async {
                executor.io {
                    active++
                    peak = maxOf(peak, active)
                    delay(10)
                    active--
                    value
                }
            }
        }.awaitAll()
        assertEquals((1..12).toList(), results)
        assertEquals(2, peak)
        assertEquals(0, active)
    }

    @Test
    fun `computation defaults to one operation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = TaskExecutor(AppDispatchers(dispatcher, dispatcher))
        var active = 0
        var peak = 0
        List(4) {
            async {
                executor.computation {
                    active++
                    peak = maxOf(peak, active)
                    delay(10)
                    active--
                }
            }
        }.awaitAll()
        assertEquals(1, peak)
    }

    @Test
    fun `cancelled waiter never starts and cancellation releases running permit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = TaskExecutor(AppDispatchers(dispatcher, dispatcher), ioParallelism = 1)
        val entered = CompletableDeferred<Unit>()
        var resourceClosed = false
        val running = launch {
            executor.io {
                try {
                    entered.complete(Unit)
                    awaitCancellation()
                } finally {
                    resourceClosed = true
                }
            }
        }
        entered.await()
        var waiterEntered = false
        val waiting = launch { executor.io { waiterEntered = true } }
        runCurrent()
        waiting.cancelAndJoin()
        running.cancelAndJoin()
        assertFalse(waiterEntered)
        assertTrue(resourceClosed)
        assertEquals(42, executor.io { 42 })
    }

    @Test
    fun `operation exception propagates and releases permit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = TaskExecutor(AppDispatchers(dispatcher, dispatcher), ioParallelism = 1)
        val original = IOException("unavailable")
        try {
            executor.io { throw original }
            fail("Must propagate the original failure")
        } catch (actual: IOException) {
            // 协程调试的堆栈恢复可能复制异常；校验错误语义，不能要求对象引用完全相同。
            assertEquals(original.message, actual.message)
        }
        assertEquals(7, executor.io { 7 })
    }
}
