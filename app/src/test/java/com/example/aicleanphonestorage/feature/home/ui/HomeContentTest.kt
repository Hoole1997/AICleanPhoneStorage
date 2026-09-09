package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeToolMetric
import com.example.aicleanphonestorage.feature.home.data.HomeToolMetrics
import com.example.aicleanphonestorage.feature.home.data.ScanSummary
import com.example.aicleanphonestorage.feature.home.data.StorageSummary
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HomeContentTest {
    @Test
    fun `unknown device data never becomes preview numbers`() {
        val content = HomeOverview().toHomeContent(Locale.US)
        assertNull(content.hero.value)
        assertNull(content.statistics.download)
        assertNull(content.statistics.available)
        assertTrue(content.tools.all { it.detail == null })
    }

    @Test
    fun `completed zero junk still displays completion and actual storage independently`() {
        val content =
            HomeOverview(
                    StorageSummary(128_000_000_000, 40_300_000_000),
                    ScanSummary.Completed(0, 1),
                )
                .toHomeContent(Locale.US)
        assertTrue(content.hero.scanComplete)
        assertEquals("0", content.hero.value)
        assertEquals("B", content.hero.unit)
        assertEquals(31, content.hero.usedPercent)
        assertEquals("87.7GB", content.statistics.available)
    }

    @Test
    fun `tool cards format their own metrics instead of sharing placeholder storage`() {
        val overview =
            HomeOverview(
                tools =
                    HomeToolMetrics(
                        network = HomeToolMetric.Bytes(1_500_000, true),
                        notifications = HomeToolMetric.AppCount(3, true),
                        apps = HomeToolMetric.Bytes(4_000_000_000),
                        compress = HomeToolMetric.Bytes(12_500_000),
                        largeFiles = HomeToolMetric.Bytes(0),
                        unusedFiles = HomeToolMetric.NotScanned,
                        screenshots = HomeToolMetric.Unavailable,
                    )
            )
        val tools = overview.toHomeContent(Locale.US).tools.associateBy { it.tool }
        assertEquals("≥1.5MB", tools.getValue(HomeTool.Network).detail)
        assertEquals("4GB", tools.getValue(HomeTool.Apps).detail)
        assertEquals("12.5MB", tools.getValue(HomeTool.Compress).detail)
        assertEquals("0B", tools.getValue(HomeTool.LargeFiles).detail)
        assertEquals(
            HomeToolMetric.AppCount(3, true),
            tools.getValue(HomeTool.Notifications).metric,
        )
        assertNull(tools.getValue(HomeTool.Notifications).detail)
        assertNull(tools.getValue(HomeTool.UnusedFiles).detail)
        assertEquals(HomeToolMetric.NotScanned, tools.getValue(HomeTool.UnusedFiles).metric)
        assertEquals(HomeToolMetric.Unavailable, tools.getValue(HomeTool.Screenshots).metric)
    }

    @Test
    fun `download uses wifi alone and distinguishes unknown from zero`() {
        val overview =
            HomeOverview(
                tools = HomeToolMetrics(network = HomeToolMetric.Bytes(9_000_000)),
                wifiBytes = 1_500_000,
            )
        assertEquals("1.5MB", overview.toHomeContent(Locale.US).statistics.download)
        assertEquals(
            "0B",
            overview.copy(wifiBytes = 0).toHomeContent(Locale.US).statistics.download,
        )
        assertNull(overview.copy(wifiBytes = null).toHomeContent(Locale.US).statistics.download)
    }

    @Test
    fun `app count fallback is not formatted as occupied bytes`() {
        val app =
            HomeOverview(tools = HomeToolMetrics(apps = HomeToolMetric.AppCount(85)))
                .toHomeContent(Locale.US)
                .tools
                .single { it.tool == HomeTool.Apps }
        assertEquals(HomeToolMetric.AppCount(85), app.metric)
        assertNull(app.detail)
    }
}
