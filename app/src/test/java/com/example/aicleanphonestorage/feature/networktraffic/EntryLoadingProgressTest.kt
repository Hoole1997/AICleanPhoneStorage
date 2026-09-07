package com.example.aicleanphonestorage.feature.networktraffic

import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficProgress
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficStage
import com.example.aicleanphonestorage.feature.networktraffic.ui.EntryLoadingProgress
import org.junit.Assert.*
import org.junit.Test

class EntryLoadingProgressTest {
    @Test fun `85 ready apps do not immediately display 85 of 85`() {
        var clock = 0L
        val pacing = EntryLoadingProgress(0, 2500) { clock }
        pacing.report(TrafficProgress(TrafficStage.APPLICATIONS, 85, 85))
        pacing.complete(85)
        assertEquals(0, pacing.frame().detail.completed)
        assertEquals(0, pacing.frame().percent)
        clock = 1200
        assertEquals(42, pacing.frame().detail.completed)
        assertEquals(49, pacing.frame().percent)
        clock = 2399
        assertEquals(84, pacing.frame().detail.completed)
        assertTrue(pacing.frame().percent!! < 100)
        clock = 2400
        assertEquals(85, pacing.frame().detail.completed)
        assertEquals(100, pacing.frame().percent)
        assertEquals(100L, pacing.remainingMillis())
        clock = 2500
        assertEquals(0L, pacing.remainingMillis())
    }
    @Test fun `presentation never exceeds actual work or finishes before query returns`() {
        val pacing = EntryLoadingProgress(0, 2500) { 10_000L }
        pacing.report(TrafficProgress(TrafficStage.APPLICATIONS, 10, 85))
        assertEquals(10, pacing.frame().detail.completed)
        pacing.report(TrafficProgress(TrafficStage.APPLICATIONS, 85, 85))
        assertEquals(84, pacing.frame().detail.completed)
        pacing.complete(85)
        assertEquals(85, pacing.frame().detail.completed)
        assertEquals(0L, pacing.remainingMillis())
    }
    @Test fun `empty result does not invent app counts`() {
        var clock = 0L
        val pacing = EntryLoadingProgress(0, 2500) { clock }
        assertNull(pacing.frame().percent)
        pacing.complete(0)
        clock = 1200
        assertEquals(0, pacing.frame().detail.total)
        assertEquals(50, pacing.frame().percent)
    }
}
