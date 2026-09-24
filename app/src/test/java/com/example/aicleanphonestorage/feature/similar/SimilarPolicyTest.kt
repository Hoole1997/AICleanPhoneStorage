package com.example.aicleanphonestorage.feature.similar

import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.media.PhotoSignature
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.similar.data.SimilarPolicy
import org.junit.Assert.*
import org.junit.Test

class SimilarPolicyTest {
    private fun file() =
        ScannedFile(
            1,
            "content://media/external/images/media/1",
            "camera.jpg",
            "image/jpeg",
            100_000,
            2000,
            FileCategory.PHOTOS,
            FileBackend.MEDIA,
            "external",
            takenMillis = 1000,
        )

    private fun signature() = PhotoSignature(42, 1.33, 100, 30.0, 100.0, 800 * 600L)

    @Test
    fun sourceFilterExcludesScreenshotsThumbnailsAndPrivateFiles() {
        assertTrue(SimilarPolicy.candidate(file(), "DCIM/Camera/"))
        assertFalse(SimilarPolicy.candidate(file(), "Pictures/Screenshots/"))
        assertFalse(SimilarPolicy.candidate(file(), "DCIM/.thumbnails/"))
        assertFalse(SimilarPolicy.candidate(file(), "Android/data/other.app/"))
        assertFalse(SimilarPolicy.candidate(file().copy(mime = "image/gif"), "DCIM/Camera/"))
        assertFalse(
            SimilarPolicy.candidate(file().copy(backend = FileBackend.DIRECT), "DCIM/Camera/")
        )
    }

    @Test
    fun originalPreferenceUsesResolutionThenClarityThenSizeThenEarlierTime() {
        val old = file()
        val metrics = signature()
        assertTrue(
            SimilarPolicy.better(
                old.copy(id = 2),
                metrics.copy(pixels = metrics.pixels * 2, sharpness = 1.0),
                old,
                metrics,
            )
        )
        assertTrue(
            SimilarPolicy.better(old.copy(id = 2), metrics.copy(sharpness = 110.0), old, metrics)
        )
        assertTrue(SimilarPolicy.better(old.copy(id = 2, size = 150_000), metrics, old, metrics))
        assertTrue(SimilarPolicy.better(old.copy(id = 2, takenMillis = 500), metrics, old, metrics))
        assertFalse(SimilarPolicy.better(old.copy(id = 2), metrics, old, metrics))
    }

    @Test
    fun bandsCannotMissAHashWithinFourBitDistance() {
        val random = java.util.Random(7)
        repeat(200) {
            val original = random.nextLong()
            var changed = original
            repeat(4) { changed = changed xor (1L shl random.nextInt(64)) }
            val bands = SimilarPolicy.bands(original)
            val altered = SimilarPolicy.bands(changed)
            assertTrue(bands.indices.any { bands[it] == altered[it] })
        }
    }

    @Test fun widerRecallDoesNotLosePairsWithDifferencesInEveryHashBand() {
        val original = 0x123456789abcdefL
        val changed = original xor (1L shl 1) xor (1L shl 14) xor (1L shl 27) xor (1L shl 40) xor (1L shl 53)
        assertTrue(SimilarPolicy.bands(original).indices.none { SimilarPolicy.bands(original)[it] == SimilarPolicy.bands(changed)[it] })
        val random = java.util.Random(29)
        repeat(500) {
            val hash = random.nextLong()
            val flips = mutableSetOf<Int>()
            while (flips.size < SimilarPolicy.MAX_HASH_DISTANCE) flips += random.nextInt(64)
            val nearby = flips.fold(hash) { value, bit -> value xor (1L shl bit) }
            val bands = SimilarPolicy.bands(nearby)
            assertTrue(SimilarPolicy.probes(hash).indices.any { bands[it] in SimilarPolicy.probes(hash)[it] })
        }
    }

    @Test fun exposureChangesRemainCandidatesButHashCollisionsAndDifferentColorsDoNot() {
        fun image(reverseY: Boolean = false, exposure: Int = 0) = IntArray(4096) { i ->
            30 + i % 64 + (if (reverseY) 63 - i / 64 else i / 64) + exposure
        }
        fun signature(values: IntArray) = com.example.aicleanphonestorage.core.media.PhotoMetrics
            .signature(values, 64, 64, 1600, 1200, stableSampling = true)
            .let { it.copy(red=it.mean, green=it.mean, blue=it.mean) }
        val original = signature(image())
        val brighter = signature(image(exposure=30))
        assertFalse(original.similar(brighter)) // 旧算法固定亮度差16，漏掉同一画面的调亮版本。
        assertTrue(SimilarPolicy.matches(original, brighter))
        val unrelated = signature(image(reverseY=true))
        assertEquals(original.hash, unrelated.hash)
        assertFalse(SimilarPolicy.matches(original, unrelated))
        assertFalse(SimilarPolicy.matches(original, original.copy(red=original.red+70)))
        assertFalse(SimilarPolicy.matches(original, original.copy(ratio=1.0)))
        val blank = signature(IntArray(4096) { 100 })
        assertFalse(SimilarPolicy.matches(blank, blank))
        assertTrue(SimilarPolicy.matches(original, original.copy(ratio=original.ratio*0.96)))
    }

    @Test
    fun similarConfirmationUsesTheNewRequirementSlot() {
        assertEquals("clean_confirm_duplicate", InterstitialPlacements.clean(CleanupFeature.SIMILAR_PHOTOS))
        assertEquals(
            "back_home_duplicate",
            InterstitialPlacements.exit(CleanupFeature.SIMILAR_PHOTOS),
        )
    }
}
