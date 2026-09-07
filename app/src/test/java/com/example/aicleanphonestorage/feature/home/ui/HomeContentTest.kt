package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.ScanSummary
import com.example.aicleanphonestorage.feature.home.data.StorageSummary
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    @Test fun `unknown device data never becomes preview numbers`() {
        val content = HomeOverview().toHomeContent(Locale.US)
        assertNull(content.hero.value)
        assertNull(content.statistics.download)
        assertNull(content.statistics.available)
        assertTrue(content.tools.all { it.detail == null })
    }

    @Test fun `completed zero junk still displays completion and actual storage independently`() {
        val content = HomeOverview(StorageSummary(128_000_000_000, 40_300_000_000), ScanSummary.Completed(0, 1))
            .toHomeContent(Locale.US)
        assertTrue(content.hero.scanComplete)
        assertEquals("0", content.hero.value)
        assertEquals("B", content.hero.unit)
        assertEquals(31, content.hero.usedPercent)
        assertEquals("87.7GB", content.statistics.available)
    }
}
