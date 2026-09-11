package com.example.aicleanphonestorage.core.ui.loading

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryLoadingRecoveryTest {
    @Test fun innerCancellationBecomesAFailureInsteadOfLeavingTheCallerLoading() = runTest {
        val result = runCatching {
            TimedEntryLoader({ 0 }, { testScheduler.currentTime }).load(
                count = { _: Int -> 0 }, onFrame = {},
            ) { throw CancellationException("provider aborted") }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(coroutineContext.isActive)
    }

    @Test fun externalCancellationIsNotConvertedIntoAFailedScan() = runTest {
        var reportedFailure = false
        val load = launch {
            try {
                TimedEntryLoader({ 0 }, { testScheduler.currentTime }).load(
                    count = { _: Int -> 0 }, onFrame = {}, onStalled = { reportedFailure = true },
                ) { awaitCancellation() }
            } catch (error: CancellationException) { throw error }
            catch (_: IOException) { reportedFailure = true }
        }
        runCurrent(); load.cancelAndJoin()
        assertFalse(reportedFailure)
    }

    @Test fun stalledProviderPublishesFailureBeforeBlockingCleanupFinishes() = runTest {
        var stalled = false
        var failure: Throwable? = null
        var success = false
        val load = launch {
            try {
                TimedEntryLoader({ 2000 }, { testScheduler.currentTime }, 1000).load(
                    count = { _: Int -> 0 }, onFrame = {}, onStalled = { stalled = true },
                ) { report ->
                    report(TaskProgress("FILES", 4))
                    withContext(NonCancellable) { delay(5000) }
                    4
                }
                success = true
            } catch (error: Exception) { failure = error }
        }
        runCurrent(); advanceTimeBy(1000); runCurrent()
        assertTrue(stalled)
        assertFalse(load.isCompleted)
        advanceUntilIdle()
        assertFalse(success)
        assertTrue(failure is EntryLoadingStalledException)
    }

    @Test fun realProgressKeepsLongScansAliveEvenIfIntegerPercentageDoesNotChange() = runTest {
        var stalled = false
        val loader = TimedEntryLoader({ 0 }, { testScheduler.currentTime }, 1000)
        val result = loader.load(count = { _: Int -> 10 }, onFrame = {}, onStalled = { stalled = true }) { report ->
            repeat(10) {
                delay(600)
                report(TaskProgress("FILES", it + 1, 10000))
            }
            10
        }
        assertEquals(10, result)
        assertFalse(stalled)
    }

    @Test fun repeatingTheSameReportDoesNotKeepAStalledTaskAlive() = runTest {
        var stalled = false
        val result = runCatching {
            TimedEntryLoader({ 0 }, { testScheduler.currentTime }, 1000).load(
                count = { _: Int -> 0 }, onFrame = {}, onStalled = { stalled = true },
            ) { report ->
                while (true) { report(TaskProgress("FILES", 2)); delay(100) }
                @Suppress("UNREACHABLE_CODE") 0
            }
        }
        assertTrue(stalled)
        assertTrue(result.exceptionOrNull() is EntryLoadingStalledException)
    }
}
