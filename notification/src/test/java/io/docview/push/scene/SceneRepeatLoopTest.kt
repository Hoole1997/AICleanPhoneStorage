package io.docview.push.scene

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SceneRepeatLoopTest {
    @Test fun idsRepeatIndependentlyEveryFourSecondsForConfiguredAdditionalCount() = runTest {
        val sent = mutableListOf<String>()
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, 2) }
        loop.start(13101, "a") { count, _ -> sent += "a:$count"; true }
        loop.start(13102, "b") { count, _ -> sent += "b:$count"; true }
        runCurrent(); advanceTimeBy(3999); runCurrent(); assertTrue(sent.isEmpty())
        advanceTimeBy(1); runCurrent(); assertEquals(listOf("a:0", "b:0"), sent)
        advanceTimeBy(4000); runCurrent(); assertEquals(listOf("a:0", "b:0", "a:1", "b:1"), sent)
        advanceTimeBy(12000); runCurrent(); assertEquals(4, sent.size)
    }

    @Test fun replacingOneIdDoesNotResetOrStopOtherIdsAndOldDismissTokenIsIgnored() = runTest {
        val sent = mutableListOf<String>()
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, 2) }
        loop.start(1, "old") { _, _ -> sent += "old"; true }
        loop.start(2, "other") { count, _ -> sent += "other:$count"; true }
        runCurrent(); advanceTimeBy(2000)
        loop.start(1, "new") { count, _ -> sent += "new:$count"; true }
        loop.cancel(1, "old")
        runCurrent(); advanceTimeBy(2000); runCurrent(); assertEquals(listOf("other:0"), sent)
        advanceTimeBy(2000); runCurrent(); assertEquals(listOf("other:0", "new:0"), sent)
        loop.cancel(1, "new")
        advanceTimeBy(2000); runCurrent(); assertEquals(listOf("other:0", "new:0", "other:1"), sent)
    }

    @Test fun disabledOrZeroCountDoesNotRepostAndRemoteSwitchStopsAlreadyRunningLoops() = runTest {
        var rule = SceneRepeatPolicy(false, 3)
        var sent = 0
        val loop = SceneRepeatLoop(this) { rule }
        loop.start(1, "off") { _, _ -> sent++; true }
        rule = SceneRepeatPolicy(true, 0)
        loop.start(2, "zero") { _, _ -> sent++; true }
        advanceTimeBy(8000); runCurrent(); assertEquals(0, sent)
        rule = SceneRepeatPolicy(true, 3)
        loop.start(1, "on") { _, _ -> sent++; true }
        runCurrent(); advanceTimeBy(4000); runCurrent(); assertEquals(1, sent)
        rule = SceneRepeatPolicy(false, 3)
        advanceTimeBy(8000); runCurrent(); assertEquals(1, sent)
    }

    @Test fun changedLoopCountIsReadBeforeEveryRepeat() = runTest {
        var limit = 5
        var sent = 0
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, limit) }
        loop.start(1, "one") { _, _ -> sent++; true }
        runCurrent(); advanceTimeBy(4000); runCurrent()
        limit = 1
        advanceTimeBy(20000); runCurrent(); assertEquals(1, sent)
    }

    @Test fun failedOrInvalidNotificationStopsOnlyItself() = runTest {
        val sent = mutableListOf<Int>()
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, 3) }
        loop.start(1, "removed") { _, _ -> false }
        loop.start(2, "denied") { _, _ -> error("System rejected") }
        loop.start(3, "valid") { _, _ -> sent += 3; true }
        runCurrent(); advanceTimeBy(16000); runCurrent(); assertEquals(listOf(3, 3, 3), sent)
    }

    @Test fun staleAsyncBuildCannotPublishAfterReplacement() = runTest {
        val build = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, 1) }
        loop.start(1, "old") { _, current -> build.await(); if (current()) sent += "old"; current() }
        runCurrent(); advanceTimeBy(4000); runCurrent()
        loop.start(1, "new") { _, current -> if(current()) sent += "new"; current() }
        build.complete(Unit)
        runCurrent(); advanceTimeBy(4000); runCurrent(); assertEquals(listOf("new"), sent)
    }

    @Test fun serviceCloseCancelsAllAndPreventsRestart() = runTest {
        var sent = 0
        val loop = SceneRepeatLoop(this) { SceneRepeatPolicy(true, 3) }
        loop.start(1, "one") { _, _ -> sent++; true }
        loop.start(2, "two") { _, _ -> sent++; true }
        runCurrent(); loop.close()
        loop.start(3, "late") { _, _ -> sent++; true }
        advanceTimeBy(20000); runCurrent(); assertEquals(0, sent)
    }
}
