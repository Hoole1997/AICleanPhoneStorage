package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把已经校验过的大文件视频映射为同一内容的 MediaStore.Video URI，不改变扫描来源/选中快照。
 * 只按完整路径或系统 document→media 映射查找，禁止按名称/大小猜测；每次最多保留一个候选。
 * 未被媒体库索引或不支持系统映射的文档继续使用原授权来源，不主动扫描/插入媒体库。
 */
internal class VideoDeletionTargetResolver(context: Context, private val content: FileContentAccess) {
    private val app = context.applicationContext

    suspend fun resolve(file: ScannedFile): ScannedFile? {
        if (file.backend == FileBackend.MEDIA) return file
        if (file.backend == FileBackend.DOCUMENT) {
            if (Build.VERSION.SDK_INT < 29) return null
            val document = Uri.parse(file.uri)
            if (document.authority !in setOf("com.android.externalstorage.documents", "com.android.providers.media.documents")) return null
            // ExternalStorageProvider 的公开转换从 Android 12 才支持；旧系统保留 SAF 授权路径。
            if (document.authority == "com.android.externalstorage.documents" && Build.VERSION.SDK_INT < 31) return null
            val media = MediaStore.getMediaUri(app, document) ?: return null
            if (media.authority != MediaStore.AUTHORITY) throw IOException("Video media identity mismatch")
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.getVolumeName(media))
            return query(file, collection, "${MediaStore.MediaColumns._ID}=?", arrayOf(ContentUris.parseId(media).toString()), null)
        }
        val path = content.validatedFile(file).canonicalPath
        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(app) else setOf("external")
        var match: ScannedFile? = null
        for (volume in volumes) {
            val row = query(file, MediaStore.Video.Media.getContentUri(volume),
                "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), path) ?: continue
            if (match != null) throw IOException("Video media identity ambiguous")
            match = row
        }
        return match
    }

    @Suppress("DEPRECATION")
    private suspend fun query(original: ScannedFile, collection: Uri, selection: String, args: Array<String>, path: String?): ScannedFile? =
        suspendCancellableCoroutine { waiting ->
            val signal = CancellationSignal()
            waiting.invokeOnCancellation { signal.cancel() }
            try {
                val columns = mutableListOf("_id", "_size", "date_modified", "mime_type", "_data")
                if (Build.VERSION.SDK_INT >= 29) columns += "is_pending"
                if (Build.VERSION.SDK_INT >= 30) columns += "is_trashed"
                val cursor = content.resolver.query(collection, columns.toTypedArray(), selection, args, null, signal)
                    ?: throw IOException("Video media query unavailable")
                val result = cursor.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val size = cursor.getLong(1)
                    val modified = cursor.getLong(2) * 1000
                    val mime = cursor.getString(3).orEmpty()
                    val actualPath = cursor.getString(4).orEmpty()
                    if (path != null && path != actualPath) throw IOException("Video media identity mismatch")
                    if (!mime.startsWith("video/") || size != original.size ||
                        (original.modifiedMillis > 0 && (modified <= 0 || modified / 1000 != original.modifiedMillis / 1000)))
                        throw IOException("Video media metadata changed")
                    if ((Build.VERSION.SDK_INT >= 29 && cursor.getInt(5) != 0) ||
                        (Build.VERSION.SDK_INT >= 30 && cursor.getInt(6) != 0)) throw IOException("Video media unavailable")
                    // MediaStore 时间精度为秒；原来源的精确时间仍由引擎在映射前后分别校验，不放宽原校验。
                    val row = original.copy(uri = ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                        backend = FileBackend.MEDIA, scope = collection.toString(), path = actualPath,
                        modifiedMillis = modified, mime = mime)
                    if (cursor.moveToNext()) throw IOException("Video media identity ambiguous")
                    row
                }
                if (waiting.isActive) waiting.resume(result)
            } catch (error: Exception) {
                if (waiting.isActive) waiting.resumeWithException(error)
            }
        }
}
