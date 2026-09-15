package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** 只扫描元数据。三种来源共用emit接口，文件内容直到缩略图/用户操作时才读取。 */
internal class FileScanSources(context: Context, private val index: ScanIndex) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver

    suspend fun scan(
        access: ScanAccess,
        scanId: Long,
        emit: (ScannedFile, String) -> Unit,
        includeEmptyDirectories: Boolean = false,
        directories: DirectoryScanPolicy? = null,
        progress: (Int, Int?) -> Unit,
    ): Int {
        var seen = 0
        val context = currentCoroutineContext()
        when (access.source) {
            ScanSourceKind.MEDIA -> {
                val volumes =
                    if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(app).toList()
                    else listOf("external")
                for (volume in volumes) {
                    val collection = MediaStore.Images.Media.getContentUri(volume)
                    val columns =
                        mutableListOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATA,
                            MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.MIME_TYPE,
                            MediaStore.MediaColumns.DATE_MODIFIED,
                            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                        )
                    if (Build.VERSION.SDK_INT >= 29)
                        columns += MediaStore.MediaColumns.RELATIVE_PATH
                    val selection = buildString {
                        append("${MediaStore.MediaColumns.SIZE}>0")
                        if (Build.VERSION.SDK_INT >= 29)
                            append(" AND ${MediaStore.MediaColumns.IS_PENDING}=0")
                        if (Build.VERSION.SDK_INT >= 30)
                            append(" AND ${MediaStore.MediaColumns.IS_TRASHED}=0")
                    }
                    query(collection, columns.toTypedArray(), selection) { cursor ->
                        while (cursor.moveToNext()) {
                            context.ensureActive()
                            val uri =
                                ContentUris.withAppendedId(
                                    collection,
                                    cursor.long(MediaStore.MediaColumns._ID),
                                )
                            val name = cursor.text(MediaStore.MediaColumns.DISPLAY_NAME)
                            val mime =
                                cursor.text(MediaStore.MediaColumns.MIME_TYPE).ifBlank {
                                    mime(name)
                                }
                            val folder =
                                if (Build.VERSION.SDK_INT >= 29)
                                    cursor.text(MediaStore.MediaColumns.RELATIVE_PATH)
                                else cursor.text(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                            emit(
                                ScannedFile(
                                    uri = uri.toString(),
                                    name = name,
                                    mime = mime,
                                    size = cursor.long(MediaStore.MediaColumns.SIZE),
                                    modifiedMillis =
                                        cursor.long(MediaStore.MediaColumns.DATE_MODIFIED) * 1000,
                                    category = CleanupPolicy.category(name, mime),
                                    backend = FileBackend.MEDIA,
                                    scope = collection.toString(),
                                    path = cursor.text(MediaStore.MediaColumns.DATA),
                                ),
                                folder,
                            )
                            seen++
                            if (seen % 50 == 0) progress(seen, null)
                        }
                    }
                }
            }
            ScanSourceKind.DIRECT -> {
                seen = DirectStorageScanner(::mime).scan(access.roots, includeEmptyDirectories, emit, progress)
            }
            ScanSourceKind.DOCUMENT -> {
                seen = DocumentTreeScanner(app, index, ::mime).scan(
                    Uri.parse(access.roots.single()), scanId, includeEmptyDirectories, emit, directories, progress,
                )
            }
            null -> throw SecurityException("Storage access required")
        }
        progress(seen, seen)
        return seen
    }

    /** CancellationSignal连接协程取消；Cursor始终在工作线程中关闭，不将其交给UI。 */
    private suspend fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        consume: (Cursor) -> Unit,
    ) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                resolver.query(uri, projection, selection, null, null, signal)?.use(consume)
                    ?: throw IOException("Provider returned no cursor")
                if (continuation.isActive) continuation.resume(Unit)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private fun mime(name: String) =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
            ?: "application/octet-stream"

    private fun Cursor.text(name: String): String =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }

    private fun Cursor.long(name: String): Long =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
}
