package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** 单 Cursor 扫描用户授权的 SAF 树；目录名使用提供者元数据，不能从不透明 documentId 猜路径。 */
internal class DocumentTreeScanner(context: Context, index: ScanIndex, private val mime: (String) -> String) {
    private val resolver = context.applicationContext.contentResolver
    private val directories = DirectoryScanIndex(index)

    suspend fun scan(tree: Uri, scan: Long, includeEmptyDirectories: Boolean, emit: (ScannedFile, String) -> Unit, progress: (Int, Int?) -> Unit): Int {
        val context = currentCoroutineContext()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        var rootName = ""
        query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId)) { cursor ->
            if (!cursor.moveToFirst()) throw IOException("Directory unavailable")
            rootName = cursor.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        }
        directories.enqueue(scan, ScanDirectory(rootId, name = rootName, folder = rootName))
        var seen = 0
        while (true) {
            context.ensureActive()
            val directory = directories.take(scan) ?: break
            query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.document)) { cursor ->
                while (cursor.moveToNext()) {
                    context.ensureActive()
                    val id = cursor.text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val name = cursor.text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val type = cursor.text(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modified = cursor.number(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (type == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val entry = ScanDirectory(id, directory.document, name, "${directory.folder}/$name", modified, directory.depth + 1)
                        // 深度截断/提供者循环不是空目录；在父链上传播，避免误判整棵子树为空。
                        if (entry.depth >= 64 || !directories.enqueue(scan, entry)) directories.markNonempty(scan, directory.document)
                    } else {
                        directories.markNonempty(scan, directory.document)
                        emit(ScannedFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name = name,
                            mime = type.ifBlank { mime(name) }, size = cursor.number(DocumentsContract.Document.COLUMN_SIZE),
                            modifiedMillis = modified, category = CleanupPolicy.category(name, type),
                            backend = FileBackend.DOCUMENT, scope = tree.toString(),
                        ), directory.folder)
                    }
                    if (++seen % 50 == 0) progress(seen, null)
                }
            }
        }
        if (includeEmptyDirectories) {
            while (true) {
                context.ensureActive()
                val entry = directories.take(scan, completed = true) ?: break
                val parent = entry.parent ?: continue // 授权根目录不参与清理。
                if (entry.nonempty) directories.markNonempty(scan, parent)
                else emit(ScannedFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.document).toString(), name = entry.name,
                    mime = DocumentsContract.Document.MIME_TYPE_DIR, size = 0, modifiedMillis = entry.modified,
                    category = FileCategory.OTHER, backend = FileBackend.DOCUMENT, scope = tree.toString(),
                ), entry.folder)
                if (++seen % 50 == 0) progress(seen, null)
            }
        }
        return seen
    }

    private suspend fun query(uri: Uri, consume: (Cursor) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            resolver.query(uri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ), null, null, null, signal)?.use(consume) ?: throw IOException("Directory cannot be read")
            if (continuation.isActive) continuation.resume(Unit)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    private fun Cursor.text(name: String) = getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }
    private fun Cursor.number(name: String) = getColumnIndex(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
}
