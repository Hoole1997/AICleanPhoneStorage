package com.example.aicleanphonestorage.feature.videos.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.net.toUri
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 不取原尺寸帧。系统缩略图按需加载，共享执行器最多并行两项，LRU 上限 8MB。 */
internal class VideoThumbnailLoader(context: Context, private val executor: TaskExecutor) {
    private val resolver = context.applicationContext.contentResolver
    private val cache = object : LruCache<String, Bitmap>(minOf(8 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    // 防止不可见时已取消的同步 Provider 调用晚到后重新填充缓存。
    private var generation = 0
    @Suppress("DEPRECATION")
    suspend fun load(file: ScannedFile): Bitmap? {
        val epoch = generation
        val key = "${file.uri}:${file.size}:${file.modifiedMillis}"
        cache.get(key)?.let { return it }
        val bitmap = executor.io {
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                val signal = CancellationSignal()
                val uri = file.uri.toUri()
                continuation.invokeOnCancellation {
                    signal.cancel()
                    if (Build.VERSION.SDK_INT < 29) MediaStore.Video.Thumbnails.cancelThumbnailRequest(resolver, ContentUris.parseId(uri))
                }
                try {
                    val result = if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(uri, Size(320, 320), signal)
                    else MediaStore.Video.Thumbnails.getThumbnail(resolver, ContentUris.parseId(uri), MediaStore.Video.Thumbnails.MICRO_KIND, null)
                    if (continuation.isActive) continuation.resume(result) else result?.recycle()
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        if (e is SecurityException || e is java.io.IOException || e is IllegalArgumentException)
                            continuation.resume(null)
                        else continuation.resumeWithException(e)
                    }
                }
            }
        }
        if (epoch == generation && bitmap != null) cache.put(key, bitmap)
        return bitmap
    }
    fun clear() { generation++; cache.evictAll() }
}
