package com.example.aicleanphonestorage.feature.junkcleaner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 两阶段分析：同尺寸候选流式 SHA-256，然后小缩略图特征。一次只解码一张，不做全量两两比较。 */
internal class JunkPhotoAnalyzer(context: Context, private val index: JunkIndex) {
    private val content = FileContentAccess(context)

    private data class Reference(
        var file: ScannedFile,
        var signature: PhotoSignature,
        var grouped: Boolean = false,
        val groupKey: String = "similar:${file.id}",
        val anchorTime: Long = file.modifiedMillis,
    )

    suspend fun analyze(scan: Long, report: (Int) -> Unit): Int {
        var done = 0
        var skipped = 0
        var after = 0L
        while (true) {
            val batch = index.photos(scan, after, hashCandidates = true)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                after = file.id
                try {
                    index.setHash(file.id, content.fingerprint(file))
                } catch (error: Exception) {
                    expected(error)
                    index.setHash(file.id, "!")
                    skipped++
                }
                report(++done)
            }
        }
        index.duplicateGroups(scan)
        // 以修改时间顺序维护最多 64 张参考图；仅比较七天内、相近比例和亮度的图，限制计算与误报。
        val references = ArrayDeque<Reference>(64)
        var modified = Long.MIN_VALUE
        after = 0
        while (true) {
            val batch = index.photosByTime(scan, modified, after)
            if (batch.isEmpty()) break
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                modified = file.modifiedMillis
                after = file.id
                try {
                    val signature = signature(file)
                    if (signature == null) {
                        skipped++
                        continue
                    }
                    val match =
                        references.firstOrNull {
                            file.modifiedMillis > 0 &&
                                it.file.modifiedMillis > 0 &&
                                file.modifiedMillis - it.anchorTime in 0..7L * 86_400_000 &&
                                signature.similar(it.signature)
                        }
                    if (match != null) {
                        val group = match.groupKey
                        val keepNew =
                            signature.pixels > match.signature.pixels ||
                                (signature.pixels == match.signature.pixels &&
                                    file.size > match.file.size)
                        index.mark(match.file.id, JunkKind.SIMILAR, group, !keepNew)
                        index.mark(file.id, JunkKind.SIMILAR, group, keepNew)
                        match.grouped = true
                        if (keepNew) {
                            match.file = file
                            match.signature = signature
                        }
                    } else {
                        if (signature.needsReview) index.mark(file.id, JunkKind.REVIEW_QUALITY)
                        references.addLast(Reference(file, signature))
                        if (references.size > 64) references.removeFirst()
                    }
                } catch (error: Exception) {
                    expected(error)
                    skipped++
                } finally {
                    report(++done)
                }
            }
        }
        return skipped
    }

    private suspend fun signature(file: ScannedFile): PhotoSignature? {
        if (file.mime !in setOf("image/jpeg", "image/png")) return null
        content.validate(file)
        if (file.mime == "image/png")
            com.example.aicleanphonestorage.feature.filecleaner.operations
                .ImageContentChecks(content)
                .requireStaticPng(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        content.input(file).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        var image =
            content.input(file).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        try {
            currentCoroutineContext().ensureActive()
            val orientation =
                content.input(file).use {
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
                val previous = image
                image =
                    Bitmap.createBitmap(
                        previous,
                        0,
                        0,
                        previous.width,
                        previous.height,
                        matrix,
                        true,
                    )
                if (previous !== image) previous.recycle()
            }
            val small = Bitmap.createScaledBitmap(image, 64, 64, true)
            val pixels = IntArray(64 * 64)
            try {
                small.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            } finally {
                if (small !== image) small.recycle()
            }
            var red = 0
            var green = 0
            var blue = 0
            for (i in pixels.indices) {
                val rgb = pixels[i]
                val r = (rgb ushr 16) and 255
                val g = (rgb ushr 8) and 255
                val b = rgb and 255
                red += r
                green += g
                blue += b
                pixels[i] = (r * 299 + g * 587 + b * 114) / 1000
            }
            red /= pixels.size
            green /= pixels.size
            blue /= pixels.size
            content.validate(file)
            val rotated = orientation in 5..8
            return PhotoMetrics.signature(
                    pixels,
                    64,
                    64,
                    if (rotated) bounds.outHeight else bounds.outWidth,
                    if (rotated) bounds.outWidth else bounds.outHeight,
                )
                .copy(red = red, green = green, blue = blue)
        } finally {
            image.recycle()
        }
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
