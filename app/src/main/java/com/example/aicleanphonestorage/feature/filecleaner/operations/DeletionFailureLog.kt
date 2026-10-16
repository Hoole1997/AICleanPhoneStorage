package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.util.Log
import com.example.aicleanphonestorage.feature.filecleaner.data.*

/** 日志只含本地操作编号、来源和固定原因码，不打印文件名/路径/URI 或 Provider 异常原文。 */
internal object DeletionFailureLog {
    const val TAG = "FileCleanupDelete"

    fun failure(operation: Long, file: ScannedFile, feature: CleanupFeature?, stage: String, error: Exception) {
        Log.w(TAG, "op=$operation item=${file.id} feature=$feature backend=${file.backend} category=${file.category} " +
            "stage=$stage reason=${reason(error)} exception=${error.javaClass.simpleName}${snapshotDetails(error)}")
    }

    fun route(operation: Long, file: ScannedFile, mapped: Boolean) {
        Log.d(TAG, "op=$operation item=${file.id} feature=LARGE_FILES backend=${file.backend} " +
            "stage=resolve_video route=${if (mapped) "media_store" else "original_source"}")
    }

    internal fun snapshotDetails(error: Exception): String = if (error is FileSnapshotChangedException) {
        " scanned_bytes=${error.scannedBytes} current_bytes=${error.currentBytes}" +
            " scanned_mtime_ms=${error.scannedModifiedMillis} current_mtime_ms=${error.currentModifiedMillis}" +
            " metadata_source=basic_attributes"
    } else ""

    internal fun reason(error: Exception): String = when (error.message) {
        "File changed since scan" -> "file_changed_since_scan"
        "File missing" -> "file_missing"
        "File unavailable" -> "file_unavailable"
        "File outside authorized root" -> "outside_authorized_root"
        "Private app directory" -> "protected_directory"
        "Symbolic link skipped" -> "symbolic_link"
        "Selection no longer available" -> "selection_unavailable"
        "Reference photo is protected" -> "reference_protected"
        "Deletion rejected" -> "delete_returned_false"
        "Video media identity mismatch" -> "video_identity_mismatch"
        "Video media identity ambiguous" -> "video_identity_ambiguous"
        "Video media metadata changed" -> "video_metadata_changed"
        "Video media unavailable" -> "video_unavailable"
        "Video media query unavailable" -> "video_query_unavailable"
        "System deletion not verified" -> "system_delete_not_verified"
        else -> when (error) {
            is java.nio.file.NoSuchFileException -> "file_missing"
            is java.nio.file.AccessDeniedException -> "access_denied"
            is SecurityException -> "access_denied"
            is java.io.IOException -> "io_error"
            is IllegalArgumentException -> "invalid_argument"
            else -> "unexpected_error"
        }
    }
}
