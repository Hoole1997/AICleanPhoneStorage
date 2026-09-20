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

    @Test
    fun similarConfirmationUsesTheNewRequirementSlot() {
        assertEquals("clean_confirm_duplicate", InterstitialPlacements.clean(CleanupFeature.SIMILAR_PHOTOS))
        assertEquals(
            "back_home_duplicate",
            InterstitialPlacements.exit(CleanupFeature.SIMILAR_PHOTOS),
        )
    }
}
