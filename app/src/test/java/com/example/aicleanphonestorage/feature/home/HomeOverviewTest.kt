package com.example.aicleanphonestorage.feature.home

import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.ScanSummary
import com.example.aicleanphonestorage.feature.home.data.StorageSummary
import org.junit.Assert.*
import org.junit.Test

class HomeOverviewTest {
    @Test
    fun `zero junk result is different from never scanned`() {
        val completed = HomeOverview(scan = ScanSummary.Completed(0, 1))
        assertNotEquals(HomeOverview(), completed)
        assertTrue(completed.scan is ScanSummary.Completed)
        assertNull(HomeOverview().storage)
    }

    @Test
    fun `large capacities do not overflow when computing usage`() {
        val storage = StorageSummary(Long.MAX_VALUE, Long.MAX_VALUE - 100)
        assertEquals(100L, storage.availableBytes)
        assertTrue(storage.usedFraction in 0.0..1.0)
        assertEquals(0.0, StorageSummary(100, 0).usedFraction, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject usage exceeding total capacity`() {
        StorageSummary(100, 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject negative junk size`() {
        ScanSummary.Completed(-1, 1)
    }
}
