package com.example.aicleanphonestorage.feature.junkcleaner

import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import org.junit.Assert.*
import org.junit.Test

class JunkRulesTest {
    private val now = 2_000_000_000_000L

    private fun file(
        name: String,
        size: Long = 100,
        age: Int = 0,
        mime: String = "application/octet-stream",
    ) =
        ScannedFile(
            uri = "file:///test/$name",
            name = name,
            mime = mime,
            size = size,
            modifiedMillis = now - age * 86_400_000L,
            category = CleanupPolicy.category(name, mime),
            backend = FileBackend.DIRECT,
            scope = "/test",
            path = "/test/$name",
        )

    @Test
    fun recentTemporaryFilesAndUnknownDatesAreExcluded() {
        assertNull(JunkRules.classify(file("inprogress.crdownload", age = 6), now))
        assertEquals(JunkKind.TEMPORARY, JunkRules.classify(file("orphan.TMP", age = 7), now))
        assertNull(JunkRules.classify(file("unknown.tmp").copy(modifiedMillis = 0), now))
    }

    @Test
    fun emptyFilesAndLogsHaveIndependentAgeThresholds() {
        assertEquals(
            JunkKind.EMPTY_FILES,
            JunkRules.classify(file("empty", size = 0, age = 7), now),
        )
        assertNull(JunkRules.classify(file("recent.log", age = 29), now))
        assertEquals(JunkKind.OLD_LOGS, JunkRules.classify(file("old.log", age = 30), now))
    }

    @Test
    fun ordinaryDocumentsAreNeverJunkJustBecauseTheyAreOld() {
        assertNull(
            JunkRules.classify(file("contract.pdf", age = 900, mime = "application/pdf"), now)
        )
        assertEquals(JunkKind.INSTALLERS, JunkRules.classify(file("setup.apk"), now))
    }

    @Test
    fun compressedOutputIsNotAnalyzedAgain() {
        val photo = file("photo.jpg", mime = "image/jpeg")
        assertFalse(JunkRules.include(photo, "Pictures/AIClean/Compressed", now))
        assertTrue(JunkRules.include(photo, "DCIM/Camera", now))
    }

    private fun pattern(shift: Int = 0) =
        IntArray(64 * 64) { (it % 64 * 2 + it / 64 + shift).coerceIn(0, 255) }

    @Test
    fun smallLightingChangeKeepsSimilarityButDifferentAspectOrColorDoesNot() {
        val a = PhotoMetrics.signature(pattern(), 64, 64, 1600, 1200)
        val b = PhotoMetrics.signature(pattern(5), 64, 64, 800, 600)
        assertTrue(a.similar(b))
        assertFalse(a.similar(b.copy(ratio = 1.0)))
        assertFalse(a.copy(red = 200).similar(b.copy(red = 20)))
    }

    @Test
    fun blankImagesAreReviewCandidatesAndNotVisualDuplicateGroups() {
        val blank = PhotoMetrics.signature(IntArray(4096) { 100 }, 64, 64, 1600, 1200)
        assertTrue(blank.needsReview)
        assertFalse(blank.similar(blank))
        val small = PhotoMetrics.signature(pattern(), 64, 64, 320, 200)
        assertTrue(small.needsReview)
    }
}
