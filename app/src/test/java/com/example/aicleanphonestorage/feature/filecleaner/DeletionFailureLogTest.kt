package com.example.aicleanphonestorage.feature.filecleaner

import com.example.aicleanphonestorage.feature.filecleaner.operations.DeletionFailureLog
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileSnapshotChangedException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class DeletionFailureLogTest {
    @Test fun reasonsDistinguishValidationIdentityPermissionsAndRejectedDeletion() {
        assertEquals("file_changed_since_scan", DeletionFailureLog.reason(IOException("File changed since scan")))
        assertEquals("video_metadata_changed", DeletionFailureLog.reason(IOException("Video media metadata changed")))
        assertEquals("access_denied", DeletionFailureLog.reason(SecurityException("private path must not be logged")))
        assertEquals("delete_returned_false", DeletionFailureLog.reason(IOException("Deletion rejected")))
        assertEquals("io_error", DeletionFailureLog.reason(IOException("/storage/emulated/0/private-name.mp4")))
    }
    @Test fun mismatchLogsBothSnapshotsWithoutPaths() {
        val error = FileSnapshotChangedException(12, 13, 100123, 100456)
        assertEquals("file_changed_since_scan", DeletionFailureLog.reason(error))
        assertEquals(" scanned_bytes=12 current_bytes=13 scanned_mtime_ms=100123 current_mtime_ms=100456 metadata_source=basic_attributes",
            DeletionFailureLog.snapshotDetails(error))
        assertEquals("", DeletionFailureLog.snapshotDetails(IOException("/private/file.mp4")))
    }
}
