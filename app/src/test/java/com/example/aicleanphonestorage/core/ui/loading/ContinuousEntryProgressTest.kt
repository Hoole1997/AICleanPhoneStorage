package com.example.aicleanphonestorage.core.ui.loading

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContinuousEntryProgressTest {
    @Test
    fun unknownTotalsAndStageChangesNeverSwitchModeOrResetProgress() {
        var now = 0L
        val timeline = ContinuousEntryProgress(0, 2500, { now }, listOf("FILES", "PHOTOS"))
        val values = mutableListOf(timeline.frame().percent!!)
        assertEquals(0, values.first())
        now = 1500
        timeline.report(TaskProgress("FILES", 50))
        values += timeline.frame().percent!!
        now = 2000
        timeline.report(TaskProgress("FILES", 300, 300))
        values += timeline.frame().percent!!
        now = 2600
        timeline.report(TaskProgress("PHOTOS", 0))
        values += timeline.frame().percent!!
        timeline.report(TaskProgress("PHOTOS", 90, 100))
        values += timeline.frame().percent!!
        timeline.report(TaskProgress("PHOTOS", 95, 1000))
        values += timeline.frame().percent!!
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(values.last() < 100)
        timeline.complete(300, "FILES")
        values += timeline.frame().percent!!
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
        now += 400
        assertEquals(100, timeline.frame().percent)
    }

    @Test
    fun fastUnusedScanUsesOneWindowAndCountsDoNotJumpToFull() {
        var now = 0L
        val timeline = ContinuousEntryProgress(0, 3000, { now }, listOf("FILES"))
        timeline.report(TaskProgress("FILES", 85, 85))
        timeline.complete(85, "FILES")
        assertEquals(0, timeline.frame().detail.completed)
        now = 1500
        val frame = timeline.frame()
        assertTrue(frame.percent!! in 45..55)
        assertEquals(85L * frame.percent / 100, frame.detail.completed!!.toLong())
        timeline.report(TaskProgress("FILES", 0, 999)) // 完成后的迟到回调不能覆盖结果计数。
        now = 3000
        assertEquals(100, timeline.frame().percent)
        assertEquals(85, timeline.frame().detail.completed)
    }

    @Test
    fun slowScanFinishesWithVisibleCompletionAndNeverCompletesEarly() {
        var now = 10_000L
        val timeline = ContinuousEntryProgress(0, 2000, { now }, listOf("FILES"))
        timeline.report(TaskProgress("FILES", 1000))
        assertTrue(timeline.frame().percent!! < 100)
        timeline.complete(1000, "FILES")
        assertTrue(timeline.frame().percent!! < 100)
        assertEquals(400, timeline.remainingMillis())
        now += 150
        assertTrue(timeline.frame().percent!! in 85..99)
        now += 150
        assertEquals(100, timeline.frame().percent)
        assertEquals(100, timeline.remainingMillis())
        now += 100
        assertEquals(0, timeline.remainingMillis())
    }

    @Test
    fun emptyScanStillHasDeterminateFramesAndNoZeroOverZeroCounter() {
        var now = 0L
        val timeline = ContinuousEntryProgress(0, 2000, { now }, listOf("FILES"))
        assertEquals(0, timeline.frame().percent)
        timeline.complete(0, "FILES")
        now = 2000
        assertEquals(100, timeline.frame().percent)
        assertEquals(0, timeline.frame().detail.total)
    }

    @Test
    fun loaderStartsAtZeroAndPublishesMonotonicFrames() = runTest {
        val frames = mutableListOf<TimedEntryProgress.Frame>()
        val loader = TimedEntryLoader({ 2500 }, { testScheduler.currentTime })
        val result =
            loader.load(
                initialStage = "FILES",
                finalStage = "FILES",
                continuousStages = listOf("FILES"),
                count = { it: Int -> it },
                onFrame = frames::add,
            ) { report ->
                report(TaskProgress("FILES", 85, 85))
                85
            }
        assertEquals(85, result)
        assertEquals(2500, testScheduler.currentTime)
        assertEquals(0, frames.first().percent)
        assertTrue(frames.all { it.percent != null })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.percent!! >= a.percent!! })
        assertEquals(100, frames.last().percent)
    }
}
