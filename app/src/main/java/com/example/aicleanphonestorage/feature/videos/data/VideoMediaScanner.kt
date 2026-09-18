package com.example.aicleanphonestorage.feature.videos.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 仅 MediaStore.Video；逐项检查容器视频轨道，不解码帧、不缓存视频内容。调用者在受限 I/O 线程执行。 */
internal class VideoMediaScanner(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver

    suspend fun scan(emit: (ScannedFile, String) -> Unit, progress: (Int, Int?) -> Unit): Int {
        val context = currentCoroutineContext()
        var seen = 0
        val zone = ZoneId.systemDefault() // 一次扫描固定本地时区，避免扫描中跨时区导致同月拆组。
        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(app) else setOf("external")
        for (volume in volumes) {
            context.ensureActive()
            val collection = MediaStore.Video.Media.getContentUri(volume)
            suspendCancellableCoroutine<Unit> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    val selection = buildString {
                        append("${MediaStore.MediaColumns.SIZE}>0")
                        if (Build.VERSION.SDK_INT >= 29) append(" AND is_pending=0")
                        if (Build.VERSION.SDK_INT >= 30) append(" AND is_trashed=0")
                    }
                    resolver.query(collection, arrayOf("_id", "_display_name", "mime_type", "_size", "date_modified", "duration"), selection, null,
                        "date_modified DESC, _id DESC", signal)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            context.ensureActive()
                            val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                            seen++
                            // Provider 的旧元数据不能证明当前文件可读；逐个打开并验证视频轨道。
                            val readable = try {
                                resolver.openAssetFileDescriptor(uri, "r", signal)?.use { descriptor ->
                                    val extractor = MediaExtractor()
                                    try {
                                        extractor.setDataSource(descriptor)
                                        (0 until extractor.trackCount).any { track ->
                                            context.ensureActive()
                                            extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                                        }
                                    } finally { extractor.release() }
                                } == true
                            } catch (e: java.util.concurrent.CancellationException) { throw e }
                            catch (_: IOException) { false }
                            catch (_: SecurityException) { false }
                            catch (_: IllegalArgumentException) { false }
                            catch (_: IllegalStateException) { false }
                            if (readable) {
                                val modified = cursor.getLong(4).coerceAtLeast(0) * 1000
                                emit(ScannedFile(uri = uri.toString(), name = cursor.getString(1).orEmpty(),
                                    mime = cursor.getString(2) ?: "video/*", size = cursor.getLong(3), modifiedMillis = modified,
                                    category = FileCategory.VIDEOS, backend = FileBackend.MEDIA, scope = collection.toString(),
                                    bucket = month(modified, zone)), "")
                            }
                            if (seen % 20 == 0) progress(seen, null)
                        }
                    } ?: throw IOException("Video provider returned no cursor")
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
        progress(seen, seen)
        return seen
    }

    companion object {
        private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM", java.util.Locale.ROOT)
        fun month(millis: Long, zone: ZoneId): String = MONTH.format(Instant.ofEpochMilli(millis).atZone(zone))
    }
}
