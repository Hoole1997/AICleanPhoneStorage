package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** 只删除当下已无任何子项的目录，绝不递归删除；嵌套候选由共享操作队列先子后父处理。 */
internal class EmptyDirectoryDeleter(private val content: FileContentAccess) {
    suspend fun delete(item: ScannedFile): Boolean {
        require(item.isDirectory && item.size == 0L)
        currentCoroutineContext().ensureActive()
        return when (item.backend) {
            FileBackend.DIRECT -> {
                val directory = content.validatedPath(item)
                if (!directory.isDirectory) throw IOException("Directory changed or missing")
                // 子目录删除会改变父目录时间，不沿用文件的时间戳比较；重新核对实际子项。
                Files.newDirectoryStream(directory.toPath()).use {
                    if (it.iterator().hasNext()) throw IOException("Directory is no longer empty")
                }
                currentCoroutineContext().ensureActive()
                directory.delete() // 文件系统原子拒绝非空目录，新增文件不会被连带删除。
            }
            FileBackend.DOCUMENT -> {
                val tree = Uri.parse(item.scope)
                val uri = Uri.parse(item.uri)
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                val documentId = DocumentsContract.getDocumentId(uri)
                require(tree.authority == uri.authority && rootId != documentId &&
                    DocumentsContract.getTreeDocumentId(uri) == rootId) { "Directory outside authorized tree" }
                require(uri == DocumentsContract.buildDocumentUriUsingTree(tree, documentId))
                // 使用带授权 treeId 的 URI 查询/删除，由 DocumentsProvider 每次校验子树归属；兼容 API 26。
                query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)) {
                    if (!it.moveToFirst() || it.getString(0) != DocumentsContract.Document.MIME_TYPE_DIR)
                        throw IOException("Directory changed or missing")
                }
                query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) {
                    if (it.moveToFirst()) throw IOException("Directory is no longer empty")
                }
                currentCoroutineContext().ensureActive()
                DocumentsContract.deleteDocument(content.resolver, uri)
            }
            FileBackend.MEDIA -> throw IOException("MediaStore is not a directory source")
        }
    }

    private suspend fun query(uri: Uri, columns: Array<String>, consume: (Cursor) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            content.resolver.query(uri, columns, null, null, null, signal)?.use(consume)
                ?: throw IOException("Directory cannot be verified")
            if (continuation.isActive) continuation.resume(Unit)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
