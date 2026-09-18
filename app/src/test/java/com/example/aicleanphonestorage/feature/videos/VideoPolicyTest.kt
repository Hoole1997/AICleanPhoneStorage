package com.example.aicleanphonestorage.feature.videos

import com.example.aicleanphonestorage.feature.videos.data.VideoMediaScanner
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationSummary
import com.example.aicleanphonestorage.feature.filecleaner.ui.completionReport
import com.example.aicleanphonestorage.core.ui.completion.CompletionKind
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class VideoPolicyTest {
    @Test fun monthUsesLocalModificationTimeAtBoundary() {
        val instant = Instant.parse("2026-08-01T01:00:00Z").toEpochMilli()
        assertEquals("2026-07", VideoMediaScanner.month(instant, ZoneId.of("America/New_York")))
        assertEquals("2026-08", VideoMediaScanner.month(instant, ZoneId.of("Asia/Shanghai")))
    }
    @Test fun videoEntryFollowsScreenshotsAndNeverIncludesOtherMedia() {
        assertEquals(HomeTool.Screenshots.ordinal + 1, HomeTool.Videos.ordinal)
        val video = ScannedFile(uri="content://media/external/video/media/1", name="clip.mp4", mime="video/mp4", size=10,
            modifiedMillis=1, category=FileCategory.VIDEOS, backend=FileBackend.MEDIA, scope="external")
        assertTrue(CleanupPolicy.candidate(CleanupFeature.VIDEOS, video, "", 1))
        assertFalse(CleanupPolicy.candidate(CleanupFeature.VIDEOS, video.copy(category=FileCategory.PHOTOS), "", 1))
        assertFalse(CleanupPolicy.candidate(CleanupFeature.VIDEOS, video.copy(backend=FileBackend.DIRECT), "", 1))
    }
    @Test fun resultCountsOnlySuccessfulDeletions() {
        val report = OperationSummary(5, 2, 0, 2, 1, 0).completionReport(CleanupFeature.VIDEOS, 42)
        assertEquals(CompletionKind.VIDEOS, report.kind)
        assertEquals(2, report.completed)
        assertTrue(report.partial)
        assertFalse(report.celebrate)
    }
}
