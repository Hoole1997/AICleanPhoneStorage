package com.example.aicleanphonestorage.feature.unused

import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry
import com.example.aicleanphonestorage.feature.unused.data.*
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import org.junit.Assert.*
import org.junit.Test

class UnusedRulesTest {
    private val now = 2_000_000_000_000L
    private fun file(category: FileCategory, age: Long = 31L * 86_400_000) = ScannedFile(
        uri = "fixture", name = "report", mime = "application/octet-stream", size = 15_000_000,
        modifiedMillis = now - age, category = category, backend = FileBackend.DIRECT, scope = "/shared")

    @Test fun onlyOldDocumentsAndArchivesInTheRealDownloadRootQualify() {
        for (category in FileCategory.entries) {
            assertEquals(category in setOf(FileCategory.DOCUMENTS, FileCategory.ARCHIVES),
                UnusedRules.download(file(category), "Download/nested/report", now))
        }
        for (path in listOf("Documents/report", "Pictures/Download/report", "DownloadBackup/report", "Downloads/report"))
            assertFalse(UnusedRules.download(file(FileCategory.DOCUMENTS), path, now))
        assertFalse(UnusedRules.download(file(FileCategory.DOCUMENTS, 30L * 86_400_000), "Download/report", now))
        assertTrue(UnusedRules.download(file(FileCategory.DOCUMENTS, 30L * 86_400_000 + 1), "Download/report", now))
        assertFalse(UnusedRules.download(file(FileCategory.DOCUMENTS).copy(modifiedMillis = 0), "Download/report", now))
    }

    @Test fun orphanOwnershipRequiresExactAndroidDirectoryAndPackageSegment() {
        assertEquals("com.example.old", UnusedRules.residualOwner("Android/data/com.example.old/cache/file"))
        assertEquals("com.example.old", UnusedRules.residualOwner("Android/obb/com.example.old"))
        for (path in listOf("Download/Android/data/com.example.old", "Android/media/com.example.old", "Android/data", "Android/data/../file"))
            assertNull(UnusedRules.residualOwner(path))
    }

    @Test fun telemetryUsesThreeActualSizesAndExactGroupEnums() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val metrics = CleanupTelemetry { event, params -> calls += event to params }
        val groups = UnusedKind.entries.mapIndexed { i, kind -> UnusedGroup(kind, SelectionTotals(bytes = (i + 1) * 1_500_000L)) }
        metrics.unusedScan(groups)
        assertEquals(MetricEvent.UNUSED_SCAN_RESULT to mapOf("installed_apk_size" to 1.5, "residue_size" to 3.0, "download_size" to 4.5), calls.single())
        for (group in groups) {
            metrics.unusedGroup(group.kind)
            assertEquals(mapOf("group" to group.kind.wire), calls.last().second)
            metrics.selection(CleanupFeature.UNUSED_FILES, group.kind.bucket, false, SelectionTotals(selectedBytes = 500_000))
            assertEquals(mapOf("group" to group.kind.wire, "action" to "uncheck", "selected_size" to 0.5), calls.last().second)
        }
        val count = calls.size
        metrics.selection(CleanupFeature.UNUSED_FILES, null, true, SelectionTotals())
        assertEquals(count, calls.size)
    }
}
