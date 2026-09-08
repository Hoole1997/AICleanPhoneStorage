package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class CompressedCopy(val uri: String, val bytes: Long, val sha256: String)

/** 单张照片串行处理。输出更大时不保存；副本经字节校验后才发布，原文件从不覆盖。 */
internal class PhotoCompressor(context: Context, private val content: FileContentAccess) {
    private val app = context.applicationContext

    suspend fun compress(item: ScannedFile): CompressedCopy? {
        val context = currentCoroutineContext()
        content.validate(item)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        content.input(item).use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Unsupported image")
        val pixelBudget =
            minOf(2_000_000L, Runtime.getRuntime().maxMemory() / 96).coerceAtLeast(250_000)
        var sample = 1
        while (
            options.outWidth.toLong() / sample * (options.outHeight / sample) > pixelBudget ||
                maxOf(options.outWidth, options.outHeight) / sample > 2048
        ) sample *= 2
        if (options.outMimeType == "image/png") requireStaticPng(item)
        val exif = content.input(item).use { ExifInterface(it) }
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        context.ensureActive()
        val decode =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
        var bitmap: Bitmap? =
            content.input(item).use { BitmapFactory.decodeStream(it, null, decode) }
                ?: throw IOException("Cannot decode image")
        val folder = File(app.cacheDir, "cleanup_compression").apply { mkdirs() }
        val temporary = File.createTempFile("copy_", ".jpg", folder)
        try {
            val matrix =
                Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            setRotate(90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            setRotate(-90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                    }
                }
            if (!matrix.isIdentity) {
                val original = bitmap!!
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
            if (bitmap!!.hasAlpha()) {
                val original = bitmap!!
                bitmap =
                    Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
                        .also {
                            Canvas(it).apply {
                                drawColor(Color.WHITE)
                                drawBitmap(original, 0f, 0f, null)
                            }
                        }
                original.recycle()
            }
            context.ensureActive()
            FileOutputStream(temporary).use {
                if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, item.quality, it))
                    throw IOException("Image encoding failed")
                it.fd.sync()
            }
            bitmap?.recycle()
            bitmap = null
            ExifInterface(temporary).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                date?.let { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it) }
                saveAttributes()
            }
            context.ensureActive()
            if (temporary.length() <= 0 || temporary.length() >= item.size) return null
            content.validate(item)
            return publish(temporary, item)
        } finally {
            bitmap?.recycle()
            temporary.delete()
        }
    }

    private suspend fun requireStaticPng(item: ScannedFile) {
        content.input(item).use { stream ->
            val input = java.io.DataInputStream(stream)
            val signature = ByteArray(8)
            input.readFully(signature)
            var inspected = 8L
            while (inspected < 1_048_576) {
                currentCoroutineContext().ensureActive()
                val length = input.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4)
                input.readFully(type)
                val name = String(type, Charsets.US_ASCII)
                if (name == "acTL") throw IOException("Animated PNG is not supported")
                if (name == "IDAT" || name == "IEND") return
                if (length > 1_048_576 - inspected) throw IOException("Unsupported PNG metadata")
                var remaining = length + 4
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped > 0) remaining -= skipped
                    else {
                        if (input.read() < 0) throw IOException("Truncated PNG")
                        remaining--
                    }
                }
                inspected += length + 12
            }
            throw IOException("Unsupported PNG")
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun publish(file: File, original: ScannedFile): CompressedCopy {
        val name =
            "AIClean_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AIClean/Compressed/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val directory =
                        File(
                                Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES
                                ),
                                "AIClean/Compressed",
                            )
                            .apply { mkdirs() }
                    put(MediaStore.MediaColumns.DATA, File(directory, name).path)
                }
            }
        val uri =
            content.resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create output")
        var published = false
        try {
            val expected = MessageDigest.getInstance("SHA-256")
            content.resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        expected.update(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Cannot write output")
            val actual = MessageDigest.getInstance("SHA-256")
            var written = 0L
            content.resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    actual.update(buffer, 0, count)
                    written += count
                }
            } ?: throw IOException("Cannot verify output")
            val digest = actual.digest()
            if (written != file.length() || !MessageDigest.isEqual(expected.digest(), digest))
                throw IOException("Output verification failed")
            content.validate(original)
            if (
                Build.VERSION.SDK_INT >= 29 &&
                    content.resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    ) <= 0
            )
                throw IOException("Cannot publish output")
            published = true
            return CompressedCopy(
                uri.toString(),
                written,
                digest.joinToString("") { "%02x".format(it) },
            )
        } finally {
            if (!published)
                withContext(NonCancellable) {
                    try {
                        content.resolver.delete(uri, null, null)
                    } catch (_: SecurityException) {
                        /* 已撤权时由 MediaStore 回收未发布项。 */
                    }
                }
        }
    }
}
