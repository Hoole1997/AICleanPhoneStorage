package com.example.aicleanphonestorage.core.analytics

import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot
import net.corekit.core.controller.ChannelUserController.UserChannelType
import org.junit.Assert.*
import org.junit.Test

class BusinessTelemetryTest {
    @Test fun channelEnumUsesDocumentValuesRatherThanFlavorOrEnumValue() {
        assertEquals("paid", BusinessTelemetry.userType(UserChannelType.PAID))
        assertEquals("organic", BusinessTelemetry.userType(UserChannelType.NATURAL))
    }
    @Test fun dirtyStateUsesNumericMbAndCleanStateOmitsJunkSize() {
        assertEquals(mapOf("state" to "dirty", "junk_size" to 456.7), FeatureTelemetry.homeState(HomeCleaningSnapshot(true, 4567)))
        assertEquals(mapOf("state" to "cleaned"), FeatureTelemetry.homeState(HomeCleaningSnapshot(true, null, 100)))
        assertEquals(mapOf("state" to "cleaned"), FeatureTelemetry.homeState(HomeCleaningSnapshot(false, 4567)))
    }
    @Test fun visitsPairOnceAndBackgroundTimeIsNotAddedToNextVisit() {
        val state = PageVisitState()
        assertTrue(state.enter("home", 100))
        assertFalse(state.enter("home", 600)) // 配置重建续接。
        assertTrue(state.once("content")); assertFalse(state.once("content"))
        assertEquals(mapOf("page" to "home", "stay_duration" to 1.5), state.leave(1600))
        assertNull(state.leave(2000))
        assertTrue(state.enter("home", 10_000))
        assertTrue(state.once("content"))
        assertEquals(0.5, state.leave(10_500)!!["stay_duration"])
    }
    @Test fun fileSelectionAndResultsUseActualCountsAndDecimalMb() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val metrics = CleanupTelemetry { event, params -> calls += event to params }
        metrics.selection(CleanupFeature.SCREENSHOTS, null, false, SelectionTotals(selectedBytes = 1_500_000))
        assertEquals(MetricEvent.SHOT_CHECK to mapOf("action" to "uncheck", "selected_size" to 1.5), calls.last())
        metrics.selection(CleanupFeature.PHOTO_COMPRESS, null, true, SelectionTotals(selectedCount = 7))
        assertEquals(mapOf("action" to "check", "selected_count" to 7), calls.last().second)
        metrics.result(CleanupFeature.PHOTO_COMPRESS, CompletionReport(CompletionKind.COMPRESSION, 3, reducedBytes = 512_000))
        assertEquals(MetricEvent.PHOTO_RESULT_SHOW to mapOf("compressed_count" to 3, "saved_size" to 0.512), calls.last())
        metrics.result(CleanupFeature.SMART_CLEAN, CompletionReport(CompletionKind.CLEANUP, 0, failed = 2))
        assertEquals(mapOf("deleted_size" to 0.0), calls.last().second)
    }
    @Test fun unsupportedBusinessGroupsAreNotMislabelledOrFilledWithZero() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val metrics = CleanupTelemetry { event, params -> calls += event to params }
        metrics.junkGroup(JunkKind.EMPTY_FILES)
        metrics.junkGroup(JunkKind.OLD_LOGS)
        assertTrue(calls.isEmpty())
        metrics.junkScan(listOf(JunkCategorySummary(JunkKind.INSTALLERS, 1, 10_000_000), JunkCategorySummary(JunkKind.TEMPORARY, 1, 2_500_000)))
        assertEquals("has_junk", calls.last().second["result"])
        assertEquals(12.5, calls.last().second["total_size"])
        assertEquals(0.0, calls.last().second["empty_folder_size"])
        assertEquals(0.0, calls.last().second["ad_file_size"])
        metrics.selection(CleanupFeature.UNUSED_FILES, null, true, SelectionTotals(selectedBytes = 2_000_000))
        assertEquals(MetricEvent.UNUSED_CHECK, calls.last().first)
        assertFalse(calls.last().second.containsKey("group"))
    }
    @Test fun filtersAndApplicationBandsUseExactDocumentEnums() {
        var captured = emptyMap<String, Any>()
        val metrics = CleanupTelemetry { _, params -> captured = params }
        metrics.filter(CleanupFilter(category = FileCategory.DOCUMENTS, minimumBytes = 100_000_000, recentDays = 90))
        assertEquals(mapOf("filter_type" to "document", "filter_size" to "100mb", "filter_time" to "3m"), captured)
        assertNull(FeatureTelemetry.sizeBand(null))
        assertEquals("lt50mb", FeatureTelemetry.sizeBand(49_999_999))
        assertEquals("50_200mb", FeatureTelemetry.sizeBand(50_000_000))
        assertEquals("50_200mb", FeatureTelemetry.sizeBand(200_000_000))
        assertEquals("200_500mb", FeatureTelemetry.sizeBand(500_000_000))
        assertEquals("gt500mb", FeatureTelemetry.sizeBand(500_000_001))
    }
    @Test fun scanAndCleanClicksUseFeatureSpecificEvents() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val metrics = CleanupTelemetry { event, params -> calls += event to params }
        metrics.scan(CleanupFeature.SCREENSHOTS, SelectionTotals(count = 4, bytes = 5_000_000))
        assertEquals(MetricEvent.SHOT_SCAN_RESULT to mapOf("count" to 4, "total_size" to 5.0), calls.last())
        metrics.cleanClick(CleanupFeature.PHOTO_COMPRESS, SelectionTotals(selectedCount = 4, selectedBytes = 5_000_000))
        assertEquals(MetricEvent.PHOTO_COMPRESS_CLICK to mapOf("selected_count" to 4), calls.last())
        metrics.cleanClick(CleanupFeature.SMART_CLEAN, SelectionTotals())
        assertEquals("got_it", calls.last().second["button"])
    }
}
