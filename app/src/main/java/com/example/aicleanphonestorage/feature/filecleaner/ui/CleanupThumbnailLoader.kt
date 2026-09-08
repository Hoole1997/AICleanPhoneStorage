package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.filecleaner.data.FileCategory
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 缩略图缓存按字节计费；不使用原图尺寸解码，页面不可见时停止请求并清空缓存。 */
internal class CleanupThumbnailLoader(context: Context, private val executor: TaskExecutor) {
    private val access = FileContentAccess(context)
    private val cache =
        object :
            LruCache<String, Bitmap>(
                minOf(8 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())
            ) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }

    suspend fun load(file: ScannedFile, target: Int): Bitmap? =
        executor.io {
            if (file.category != FileCategory.PHOTOS) return@io null
            val pixels = target.coerceIn(64, 512)
            val key = "${file.uri}:${file.modifiedMillis}:${file.size}:$pixels"
            cache.get(key)?.let {
                return@io it
            }
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                access.input(file).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@io null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > pixels) sample *= 2
                currentCoroutineContext().ensureActive()
                val opts =
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                var bitmap =
                    access.input(file).use { BitmapFactory.decodeStream(it, null, opts) }
                        ?: return@io null
                val orientation =
                    access.input(file).use {
                        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                    }
                val matrix =
                    Matrix().apply {
                        when (orientation) {
                            2 -> setScale(-1f, 1f)
                            3 -> setRotate(180f)
                            4 -> setScale(1f, -1f)
                            5 -> {
                                setRotate(90f)
                                postScale(-1f, 1f)
                            }
                            6 -> setRotate(90f)
                            7 -> {
                                setRotate(-90f)
                                postScale(-1f, 1f)
                            }
                            8 -> setRotate(-90f)
                        }
                    }
                if (!matrix.isIdentity) {
                    val original = bitmap
                    bitmap =
                        Bitmap.createBitmap(
                            original,
                            0,
                            0,
                            original.width,
                            original.height,
                            matrix,
                            true,
                        )
                    if (bitmap !== original) original.recycle()
                }
                currentCoroutineContext().ensureActive()
                cache.put(key, bitmap)
                bitmap
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    fun clear() = cache.evictAll()
}
