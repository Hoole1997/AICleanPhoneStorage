package com.example.aicleanphonestorage.feature.home

import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import java.util.Locale
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class HomeCleaningStateTest {
    @Test fun repeatedHomeAndNotificationChecksReuseOneDirtyAmount() {
        var generated = 0
        var now = 0L
        val state = HomeCleaningState(true, { now }, { generated++; 4567 })
        val first = state.check()
        repeat(20) { now += 500_000; assertEquals(first, state.check()) }
        assertEquals(1, generated)
        assertEquals("456.7MB", first.cleanBadge(Locale.US))
    }

    @Test fun completionHidesBadgeImmediatelyAndNextCheckAtThreeMinutesGeneratesOnce() {
        var now = 1_000L
        var amount = 4567
        val state = HomeCleaningState(true, { now }, { amount })
        state.check()
        state.completed("first-visit")
        assertFalse(state.state.value.dirty)
        assertNull(state.state.value.cleanBadge(Locale.US))
        now += 179_999
        assertFalse(state.check().dirty)
        amount = 5678
        now++
        // 仅时间流逝不更新，无后台倒计时；下一次检查才恢复未清理。
        assertFalse(state.state.value.dirty)
        assertEquals(5678, state.check().junkTenthsMb)
        assertEquals(5678, state.check().junkTenthsMb)
    }

    @Test fun completionRecreationDoesNotRestartWindowButNewVisitDoes() {
        var now = 1_000L
        val state = HomeCleaningState(true, { now }, { 4567 })
        state.completed("visit-a")
        now += 120_000
        state.completed("visit-a")
        assertEquals(1_000L, state.state.value.lastCompletedAt)
        now += 60_000
        assertTrue(state.check().dirty)
        state.completed("visit-b")
        assertFalse(state.check().dirty)
        assertEquals(now, state.state.value.lastCompletedAt)
    }

    @Test fun naturalAudienceStaysCleanAndMemoryIsNotRestoredIntoANewProcess() {
        val natural = HomeCleaningState(false, { 1_000 }, { error("Natural users must not generate") })
        assertFalse(natural.check().dirty)
        val firstProcess = HomeCleaningState(true, { 1_000 }, { 4567 })
        firstProcess.completed("visit")
        assertFalse(firstProcess.check().dirty)
        val nextProcess = HomeCleaningState(true, { 1_000 }, { 5678 })
        assertTrue(nextProcess.check().dirty)
        assertNull(nextProcess.state.value.lastCompletedAt)
    }

    @Test fun concurrentNotificationAndHomeChecksGenerateOnlyOneValue() {
        var generated = 0
        val state = HomeCleaningState(true, { 0 }, { generated++; 4567 })
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(20) { java.util.concurrent.Callable { state.check() } })
            assertEquals(setOf(4567), results.map { it.get().junkTenthsMb }.toSet())
            assertEquals(1, generated)
        } finally { pool.shutdownNow() }
    }

    @Test fun generatedAmountsStayInRequestedRangeWithNonZeroFraction() {
        repeat(1000) { visit ->
            // 新实例模拟新未清理轮次，无等待或真实扫描。
            val amount = requireNotNull(HomeCleaningState(true, { visit.toLong() }).check().junkTenthsMb)
            assertTrue(amount in 3201..6799)
            assertNotEquals(0, amount % 10)
        }
    }
}
