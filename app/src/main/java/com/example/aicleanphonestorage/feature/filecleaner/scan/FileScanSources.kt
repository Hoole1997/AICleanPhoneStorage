package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
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
                if (Build.VERSION.SDK_INT < 30)
                    throw SecurityException("Direct access requires Android 11")
                for (rootPath in access.roots) {
                    val root = File(rootPath).canonicalFile.toPath()
                    Files.walkFileTree(
                        root,
                        EnumSet.noneOf(java.nio.file.FileVisitOption::class.java),
                        64,
                        object : SimpleFileVisitor<Path>() {
                            override fun preVisitDirectory(
                                dir: Path,
                                attrs: BasicFileAttributes,
                            ): FileVisitResult {
                                context.ensureActive()
                                val relative =
                                    root.relativize(dir).toString().lowercase(Locale.ROOT)
                                return if (
                                    relative == "android/data" ||
                                        relative == "android/obb" ||
                                        relative == "pictures/aiclean/compressed"
                                )
                                    FileVisitResult.SKIP_SUBTREE
                                else FileVisitResult.CONTINUE
                            }

                            override fun visitFile(
                                path: Path,
                                attrs: BasicFileAttributes,
                            ): FileVisitResult {
                                context.ensureActive()
                                if (attrs.isRegularFile && !attrs.isSymbolicLink) {
                                    val file = path.toFile()
                                    val type = mime(file.name)
                                    emit(
                                        ScannedFile(
                                            uri = Uri.fromFile(file).toString(),
                                            name = file.name,
                                            mime = type,
                                            size = attrs.size(),
                                            modifiedMillis = attrs.lastModifiedTime().toMillis(),
                                            category = CleanupPolicy.category(file.name, type),
                                            backend = FileBackend.DIRECT,
                                            scope = root.toString(),
                                            path = file.absolutePath,
                                        ),
                                        file.parent.orEmpty(),
                                    )
                                    seen++
                                    if (seen % 50 == 0) progress(seen, null)
                                }
                                return FileVisitResult.CONTINUE
                            }

                            override fun visitFileFailed(
                                file: Path,
                                exc: IOException,
                            ): FileVisitResult {
                                context.ensureActive()
                                return FileVisitResult.CONTINUE
                            }
                        },
                    )
                }
            }
            ScanSourceKind.DOCUMENT -> {
                val tree = Uri.parse(access.roots.single())
                index.enqueueDirectory(scanId, DocumentsContract.getTreeDocumentId(tree))
                while (true) {
                    context.ensureActive()
                    val document = index.takeDirectory(scanId) ?: break
                    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, document)
                    query(
                        children,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        ),
                    ) { cursor ->
                        while (cursor.moveToNext()) {
                            context.ensureActive()
                            val id = cursor.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val type = cursor.text(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            if (type == DocumentsContract.Document.MIME_TYPE_DIR)
                                index.enqueueDirectory(scanId, id)
                            else {
                                val name =
                                    cursor.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                                emit(
                                    ScannedFile(
                                        uri = uri.toString(),
                                        name = name,
                                        mime = type.ifBlank { mime(name) },
                                        size = cursor.long(DocumentsContract.Document.COLUMN_SIZE),
                                        modifiedMillis =
                                            cursor.long(
                                                DocumentsContract.Document.COLUMN_LAST_MODIFIED
                                            ),
                                        category = CleanupPolicy.category(name, type),
                                        backend = FileBackend.DOCUMENT,
                                        scope = tree.toString(),
                                    ),
                                    id,
                                )
                                seen++
                                if (seen % 50 == 0) progress(seen, null)
                            }
                        }
                    }
                }
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
