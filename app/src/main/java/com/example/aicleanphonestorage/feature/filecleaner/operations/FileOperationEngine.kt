package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class OperationSummary(
    val total: Int,
    val deleted: Int,
    val copied: Int,
    val failed: Int,
    val skipped: Int,
    val originalsAvailable: Int,
)

internal sealed interface OperationStep {
    data class Consent(val operation: Long, val sender: IntentSender) : OperationStep

    data class Finished(val summary: OperationSummary) : OperationStep
}

/** 删除/压缩共用持久化操作快照、进度和失败统计；一次最多读取一批元数据，不持有所有选中项。 */
internal class FileOperationEngine(
    context: Context,
    private val index: ScanIndex,
    private val executor: TaskExecutor,
) {
    private val app = context.applicationContext
    private val content = FileContentAccess(app)
    private val compressor = PhotoCompressor(app, content)
    private val lock = Mutex()

    suspend fun compress(operation: Long, progress: (Int, Int) -> Unit): OperationStep =
        lock.withLock {
            executor.io {
                val total = index.operationCount(operation)
                var done = total - index.operationCount(operation, "pending")
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val file = index.operationFiles(operation, limit = 1).firstOrNull() ?: break
                    try {
                        val result = compressor.compress(file)
                        if (result == null) index.mark(operation, file.id, "skipped")
                        else
                            index.mark(
                                operation,
                                file.id,
                                "copied",
                                result.uri,
                                result.bytes,
                                result.sha256,
                            )
                        index.unselect(file.id)
                    } catch (error: Exception) {
                        expected(error)
                        index.mark(operation, file.id, "failed")
                    }
                    progress(++done, total)
                }
                index.finishOperation(operation, "finished")
                index.refresh()
                OperationStep.Finished(summaryNow(operation))
            }
        }

    suspend fun delete(operation: Long, progress: (Int, Int) -> Unit): OperationStep =
        lock.withLock {
            executor.io {
                val total = index.operationCount(operation)
                var done = total - index.operationCount(operation, "pending")
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val batch = index.operationFiles(operation, limit = 200)
                    if (batch.isEmpty()) break
                    val media = ArrayList<ScannedFile>()
                    for (file in batch) {
                        currentCoroutineContext().ensureActive()
                        try {
                            if (file.retained) throw IOException("Reference photo is protected")
                            if (file.groupKey.isNotEmpty()) {
                                val reference =
                                    index.retainedPeer(file)
                                        ?: throw IOException("Reference photo missing")
                                content.validate(reference)
                                if (file.groupKey.startsWith("exact:")) {
                                    val expected =
                                        index.expectedFingerprint(file.id)
                                            ?: throw IOException("Duplicate proof unavailable")
                                    if (
                                        content.fingerprint(reference) != expected ||
                                            content.fingerprint(file) != expected
                                    )
                                        throw IOException("Duplicate contents changed")
                                }
                            }
                            content.validate(file)
                        if(file.path.isBlank())content.identityPath(file)?.let{index.rememberPath(file.id,it)}
                            // 删除原图前再次检查副本，避免生成后被移走/修改却仍删除原图。
                            index.output(operation, file.id)?.let { (uri, sha) ->
                                verifyCopy(uri, sha)
                            }
                            if (file.backend == FileBackend.MEDIA && Build.VERSION.SDK_INT >= 30) {
                                media += file
                                continue
                            }
                            val deleted =
                                when (file.backend) {
                                    FileBackend.DIRECT -> content.validatedFile(file).delete()
                                    FileBackend.DOCUMENT ->
                                        DocumentsContract.deleteDocument(
                                            content.resolver,
                                            Uri.parse(file.uri),
                                        )
                                    FileBackend.MEDIA ->
                                        content.resolver.delete(Uri.parse(file.uri), null, null) > 0
                                }
                            if (!deleted) throw IOException("Deletion rejected")
                            index.mark(operation, file.id, "deleted")
                            index.remove(file.id, notify = false)
                            if (file.backend == FileBackend.DIRECT)
                                MediaScannerConnection.scanFile(app, arrayOf(file.path), null, null)
                        } catch (error: Exception) {
                            if (
                                Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException
                            ) {
                                index.mark(operation, file.id, "awaiting")
                                index.finishOperation(operation, "awaiting_write")
                                index.refresh()
                                return@io OperationStep.Consent(
                                    operation,
                                    error.userAction.actionIntent.intentSender,
                                )
                            }
                            expected(error)
                            index.mark(operation, file.id, "failed")
                        }
                        progress(++done, total)
                    }
                    if (media.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                        // 系统在用户确认后执行这批删除，ActivityResult返回时再推进索引；单批远低于系统URI上限。
                        val request =
                            MediaStore.createDeleteRequest(
                                content.resolver,
                                media.map { Uri.parse(it.uri) },
                            )
                        media.forEach { index.mark(operation, it.id, "awaiting") }
                        index.finishOperation(operation, "awaiting_delete")
                        index.refresh()
                        return@io OperationStep.Consent(operation, request.intentSender)
                    }
                    index.refresh()
                }
                index.finishOperation(operation, "finished")
                OperationStep.Finished(summaryNow(operation))
            }
        }

    suspend fun consentResult(operation: Long, accepted: Boolean) =
        executor.io {
            if (!accepted) {
                index.cancelPending(operation)
                index.finishOperation(operation, "cancelled")
            } else {
                val systemDeleted = index.operationStatus(operation) == "awaiting_delete"
                index.operationFiles(operation, "awaiting", 500).forEach {
                    if (systemDeleted) {
                        index.mark(operation, it.id, "deleted")
                        index.remove(it.id, notify = false)
                    } else index.mark(operation, it.id, "pending")
                }
                index.finishOperation(operation, "running")
            }
            index.refresh()
        }

    suspend fun deleteOriginals(operation: Long) =
        executor.io { index.promoteOriginalsForDeletion(operation) }

    suspend fun summary(operation: Long) = executor.io { summaryNow(operation) }

    private fun summaryNow(operation: Long) =
        OperationSummary(
            index.operationCount(operation),
            index.operationCount(operation, "deleted"),
            index.copyCount(operation),
            index.operationCount(operation, "failed"),
            index.operationCount(operation, "skipped"),
            index.operationCount(operation, "copied"),
        )

    suspend fun cancel(operation: Long) =
        executor.io {
            index.cancelPending(operation)
            index.finishOperation(operation, "cancelled")
            index.refresh()
        }

    private suspend fun verifyCopy(uri: String, expected: String) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        content.resolver.openInputStream(Uri.parse(uri))?.use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: throw IOException("Compressed copy missing")
        if (digest.digest().joinToString("") { "%02x".format(it) } != expected)
            throw IOException("Compressed copy changed")
    }

    private fun expected(error: Exception) {
        when (error) {
            is CancellationException -> throw error
            is IOException,
            is SecurityException,
            is IllegalArgumentException -> Unit
            else -> throw error
        }
    }
}
