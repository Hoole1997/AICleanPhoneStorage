package com.example.aicleanphonestorage.feature.startup.grouping

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupingReporterTest {
    private class MemoryStore : GroupingStore {
        val values = mutableSetOf<String>()
        override fun contains(value: String) = value in values
        override fun record(value: String) { values += value }
    }

    @Test fun emptyDoesNothingAndEachDistinctValueReportsOnlyOnceAcrossInstances() = runTest {
        val store = MemoryStore()
        val sent = mutableListOf<String>()
        var value = ""
        fun reporter() = GroupingReporter({ value }, store, { sent += it; true }, StandardTestDispatcher(testScheduler))
        val first = reporter()
        first.reportCurrent()
        assertTrue(store.values.isEmpty())
        value = "A"; first.reportCurrent(); first.reportCurrent()
        value = "B"; first.reportCurrent()
        value = "A"; reporter().reportCurrent()
        assertEquals(listOf("Grouping_A", "Grouping_B"), sent)
    }

    @Test fun waitsForRemoteReadBeforeSendingAndDoesNotNormalizeTheValue() = runTest {
        val remote = CompletableDeferred<String>()
        val sent = mutableListOf<String>()
        val reporter = GroupingReporter({ remote.await() }, MemoryStore(), { sent += it; true }, StandardTestDispatcher(testScheduler))
        val job = launch { reporter.reportCurrent() }
        runCurrent(); assertTrue(sent.isEmpty())
        remote.complete(" a/中文 ")
        job.join()
        assertEquals(listOf("Grouping_ a/中文 "), sent)
    }

    @Test fun concurrentStartupAndConfigCallbacksSendOnce() = runTest {
        val sent = mutableListOf<String>()
        val reporter = GroupingReporter({ delay(1); "A" }, MemoryStore(), { sent += it; true }, StandardTestDispatcher(testScheduler))
        List(20) { launch { reporter.reportCurrent() } }.joinAll()
        assertEquals(listOf("Grouping_A"), sent)
    }

    @Test fun unavailableReporterDoesNotConsumeValue() = runTest {
        var ready = false
        val store = MemoryStore()
        var calls = 0
        val reporter = GroupingReporter({ "A" }, store, { if (ready) { calls++; true } else false }, StandardTestDispatcher(testScheduler))
        reporter.reportCurrent(); assertTrue(store.values.isEmpty())
        ready = true
        reporter.reportCurrent(); reporter.reportCurrent()
        assertEquals(1, calls)
    }

    @Test fun failedReportCanRetryAndIsNotRecorded() = runTest {
        var fail = true
        val store = MemoryStore()
        val reporter = GroupingReporter({ "A" }, store, { check(!fail); true }, StandardTestDispatcher(testScheduler))
        try { reporter.reportCurrent(); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertTrue(store.values.isEmpty())
        fail = false; reporter.reportCurrent()
        assertTrue(store.contains("A"))
    }

    @Test fun leavingStartupWhileWaitingForRemoteDoesNotSendOrRecord() = runTest {
        val store = MemoryStore()
        var sent = false
        val reporter = GroupingReporter({ awaitCancellation() }, store, { sent = true; true }, StandardTestDispatcher(testScheduler))
        val job = launch { reporter.reportCurrent() }
        runCurrent(); job.cancelAndJoin()
        assertFalse(sent); assertTrue(store.values.isEmpty())
    }
}
