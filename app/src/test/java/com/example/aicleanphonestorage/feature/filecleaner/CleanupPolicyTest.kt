package com.example.aicleanphonestorage.feature.filecleaner

import com.example.aicleanphonestorage.feature.filecleaner.data.*
import org.junit.Assert.*
import org.junit.Test

class CleanupPolicyTest {
    private val now = 2_000_000_000_000L

    private fun file(
        name: String = "photo.jpg",
        mime: String = "image/jpeg",
        size: Long = 12_000_000,
        modified: Long = now,
    ) =
        ScannedFile(
            uri = "content://media/1",
            name = name,
            mime = mime,
            size = size,
            modifiedMillis = modified,
            category = CleanupPolicy.category(name, mime),
            backend = FileBackend.MEDIA,
            scope = "media",
        )

    @Test
    fun oldFileBoundaryAndUnknownDatesAreConservative() {
        assertFalse(
            CleanupPolicy.candidate(CleanupFeature.UNUSED_FILES, file(modified = 0), "", now)
        )
        assertFalse(
            CleanupPolicy.candidate(
                CleanupFeature.UNUSED_FILES,
                file(modified = now - 30L * 86_400_000 + 1),
                "",
                now,
            )
        )
        assertTrue(
            CleanupPolicy.candidate(
                CleanupFeature.UNUSED_FILES,
                file(modified = now - 30L * 86_400_000),
                "",
                now,
            )
        )
    }

    @Test
    fun screenshotNeedsImageAndRecognizedNameOrFolder() {
        assertTrue(
            CleanupPolicy.candidate(CleanupFeature.SCREENSHOTS, file(), "Pictures/Screenshots", now)
        )
        assertTrue(
            CleanupPolicy.candidate(
                CleanupFeature.SCREENSHOTS,
                file(name = "截图_001.png", mime = "image/png"),
                "",
                now,
            )
        )
        assertFalse(
            CleanupPolicy.candidate(
                CleanupFeature.SCREENSHOTS,
                file(name = "Screenshot.txt", mime = "text/plain"),
                "",
                now,
            )
        )
        assertFalse(CleanupPolicy.candidate(CleanupFeature.SCREENSHOTS, file(), "DCIM/Camera", now))
    }

    @Test
    fun outputAndAnimatedFormatsAreNotCompressionCandidates() {
        assertFalse(
            CleanupPolicy.candidate(
                CleanupFeature.PHOTO_COMPRESS,
                file(),
                "Pictures/AIClean/Compressed/",
                now,
            )
        )
        assertFalse(
            CleanupPolicy.candidate(
                CleanupFeature.PHOTO_COMPRESS,
                file(mime = "image/gif"),
                "Pictures",
                now,
            )
        )
        assertFalse(
            CleanupPolicy.candidate(
                CleanupFeature.PHOTO_COMPRESS,
                file(size = 99_999),
                "Pictures",
                now,
            )
        )
    }

    @Test
    fun extensionCategoriesIgnoreCase() {
        assertEquals(
            FileCategory.APK,
            CleanupPolicy.category("installer.APK", "application/octet-stream"),
        )
        assertEquals(
            FileCategory.ARCHIVES,
            CleanupPolicy.category("backup.ZIP", "application/octet-stream"),
        )
        assertEquals(
            FileCategory.DOCUMENTS,
            CleanupPolicy.category("report.PDF", "application/pdf"),
        )
    }
}
