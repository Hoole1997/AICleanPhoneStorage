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
    fun temporaryExtensionsAndCacheDirectoriesDoNotRequireAnAgeThreshold() {
        for (name in listOf("active.tmp", "unknown.TEMP", "recent.log", "partial.part", "download.crdownload")) {
            assertEquals(JunkKind.TEMPORARY, JunkRules.classify(file(name).copy(modifiedMillis = 0), now))
        }
        assertEquals(JunkKind.TEMPORARY, JunkRules.classify(file("blob.bin"), now, "/shared/app/cache"))
        assertEquals(JunkKind.TEMPORARY, JunkRules.classify(file("blob.bin"), now, "/shared/app/Cache/images"))
        assertNull(JunkRules.classify(file("cache.json"), now, "/shared/Download"))
    }

    @Test
    fun apkUsesSuffixAndOrdinaryEmptyFilesAreNotEmptyFolders() {
        assertEquals(JunkKind.INSTALLERS, JunkRules.classify(file("setup.APK", size = 0), now))
        assertNull(JunkRules.classify(file("empty", size = 0), now))
        assertNull(JunkRules.classify(file("contract.pdf", age = 900), now))
        assertNull(JunkRules.classify(file("photo.jpg", mime = "image/jpeg"), now))
        assertEquals(JunkKind.EMPTY_FOLDERS, JunkRules.classify(file("folder", size = 0, mime = "vnd.android.document/directory"), now))
    }

    @Test
    fun adsHavePriorityOverCacheAndTemporaryFilesWithoutMatchingDownloadOrRoad() {
        for (folder in listOf("/shared/app/cache/ads", "/shared/.admob", "/shared/com.google.android.gms.ads", "/shared/UnityAdsCache", "/shared/applovin")) {
            assertEquals(folder, JunkKind.AD_FILES, JunkRules.classify(file("blob.tmp"), now, folder))
        }
        for (name in listOf("ad_banner.jpg", "ads.bin", "advert.data", "videoAd.mp4"))
            assertEquals(JunkKind.AD_FILES, JunkRules.classify(file(name), now, "/shared/Download"))
        for (name in listOf("road.jpg", "download.bin", "readme.pdf", "shadow.png"))
            assertNull(JunkRules.classify(file(name), now, "/shared/Download"))
        assertEquals(JunkKind.INSTALLERS, JunkRules.classify(file("setup.apk"), now, "/shared/ads/cache"))
    }

    @Test
    fun compressedOutputsAndHiddenPhotoCategoriesAreNotScanned() {
        assertFalse(JunkRules.include(file("photo.jpg", mime = "image/jpeg"), "DCIM/Camera", now))
        assertFalse(JunkRules.include(file("left.tmp"), "Pictures/AIClean/Compressed", now))
        assertEquals(listOf(JunkKind.INSTALLERS, JunkKind.TEMPORARY, JunkKind.EMPTY_FOLDERS, JunkKind.AD_FILES), JunkKind.visible)
        assertTrue(JunkKind.entries.contains(JunkKind.DUPLICATES))
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
