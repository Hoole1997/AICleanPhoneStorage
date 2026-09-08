package com.example.aicleanphonestorage.core.ui.completion

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationSummary
import com.example.aicleanphonestorage.feature.filecleaner.ui.completionReport
import org.junit.Assert.*
import org.junit.Test

class CompletionReportTest {
    @Test fun compressionDoesNotCountOriginalDeletionAsAnotherPhoto() {
        val report = OperationSummary(3, 2, 3, 0, 0, 1, 100, 200).completionReport(CleanupFeature.PHOTO_COMPRESS, 42)
        assertEquals(3, report.completed)
        assertEquals(1, report.originalsRemaining)
        assertEquals(1, report.removableOriginals)
        assertEquals(100, report.freedBytes)
    }
    @Test fun cancelledAndFailedActionsNeverCelebrate() {
        val cancelled = OperationSummary(3, 0, 0, 0, 3, 0).completionReport(CleanupFeature.LARGE_FILES, 1)
        assertFalse(cancelled.successful)
        assertFalse(cancelled.celebrate)
        val partial = OperationSummary(3, 1, 0, 1, 1, 0).completionReport(CleanupFeature.SMART_CLEAN, 1)
        assertTrue(partial.partial)
        assertFalse(partial.celebrate)
    }
    @Test fun disablingAllAutoClearRulesIsStillASuccessfulSave() {
        assertTrue(CompletionReport(CompletionKind.NOTIFICATIONS, 0).successful)
    }
}
