package com.example.aicleanphonestorage.app.ad

import com.example.aicleanphonestorage.core.ui.completion.CompletionKind
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.home.ui.*
import org.junit.Assert.*
import org.junit.Test

class NativeAdPlacementsTest {
    @Test
    fun allEighteenKeysAreUniqueAndMatchRequirementNames() {
        val keys =
            NativeAdFeature.entries.flatMap { listOf(it.featureSlot, it.resultSlot) } +
                listOf(NativeAdPlacements.HOME, NativeAdPlacements.SCAN_DIALOG)
        assertEquals(18, keys.size)
        assertEquals(18, keys.toSet().size)
        assertEquals(
            "native_feature_photo",
            NativeAdPlacements.feature(CleanupFeature.PHOTO_COMPRESS).featureSlot,
        )
        assertEquals(
            "native_feature_large",
            NativeAdPlacements.feature(CleanupFeature.LARGE_FILES).featureSlot,
        )
    }

    @Test
    fun sharedCompletionPageUsesSourceInsteadOfTreatingAllDeletesAsJunk() {
        val sources =
            mapOf(
                CleanupFeature.SMART_CLEAN to "junk",
                CleanupFeature.SCREENSHOTS to "screenshots",
                CleanupFeature.PHOTO_COMPRESS to "photo",
                CleanupFeature.LARGE_FILES to "large",
                CleanupFeature.UNUSED_FILES to "unused",
            )
        for ((source, suffix) in sources) assertEquals(
            "native_result_$suffix",
            NativeAdPlacements.result(CompletionKind.CLEANUP, source.name),
        )
        assertEquals(
            "native_result_notify",
            NativeAdPlacements.result(CompletionKind.NOTIFICATIONS, null),
        )
        assertEquals(
            "native_result_photo",
            NativeAdPlacements.result(CompletionKind.COMPRESSION, null),
        )
        assertNull(NativeAdPlacements.result(CompletionKind.CLEANUP, "unrecognized"))
    }

    @Test
    fun homeAdIsBetweenStatisticsAndManualCleanWithoutChangingPreviewRows() {
        val content = HomeContent(HeroContent(), HomeStatistics())
        assertFalse(content.rows().any { it == HomeRow.NativeAd })
        val rows = content.rows(includeNativeAd = true)
        assertTrue(rows[1] is HomeRow.Statistics)
        assertEquals(HomeRow.NativeAd, rows[2])
        assertEquals(HomeRow.Section, rows[3])
        assertEquals(1, rows.count { it == HomeRow.NativeAd })
    }
}
