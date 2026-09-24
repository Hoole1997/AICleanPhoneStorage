package com.example.aicleanphonestorage.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 有界解码及 EXIF 方向归一化；仅返回固定大小的数字特征，不持有 Bitmap。 */
internal class PhotoSignatureReader(context: Context) {
    private val content = FileContentAccess(context)

    suspend fun read(file: ScannedFile, stableSampling: Boolean = false): PhotoSignature? {
        if (
            file.mime !in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        )
            return null
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
                    stableSampling = stableSampling,
                )
                .copy(red = red, green = green, blue = blue)
        } finally {
            image.recycle()
        }
    }
}
