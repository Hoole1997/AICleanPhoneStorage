package com.example.aicleanphonestorage.feature.similar.data

import com.example.aicleanphonestorage.core.media.PhotoSignature
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.util.Locale

/** 相似仅代表供用户复核的候选。阈值沿用项目本地视觉指标，不宣称内容相同。 */
internal object SimilarPolicy {
    const val MAX_COMPARISONS = 512
    private val supported =
        setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    fun candidate(file: ScannedFile, folder: String): Boolean {
        val path = "/$folder/${file.path}/${file.name}".lowercase(Locale.ROOT)
        return file.backend == FileBackend.MEDIA &&
            file.category == FileCategory.PHOTOS &&
            file.size > 0 &&
            file.mime in supported &&
            !CleanupPolicy.screenshot(file.name, folder) &&
            !path.contains("/android/data/") &&
            !path.contains("/android/obb/") &&
            path.split('/').none {
                it == ".thumbnails" || it == "thumbnails" || it.startsWith("thumb_")
            }
    }

    fun time(file: ScannedFile) =
        (file.takenMillis.takeIf { it > 0 } ?: file.modifiedMillis).coerceAtLeast(0)

    fun better(
        new: ScannedFile,
        metrics: PhotoSignature,
        old: ScannedFile,
        previous: PhotoSignature,
    ): Boolean {
        if (metrics.pixels != previous.pixels) return metrics.pixels > previous.pixels
        if (metrics.sharpness != previous.sharpness) return metrics.sharpness > previous.sharpness
        // 明显异常的每像素体积不因“文件更大”获得更高优先级。
        val size = new.size.coerceAtMost(metrics.pixels * 32)
        val oldSize = old.size.coerceAtMost(previous.pixels * 32)
        if (size != oldSize) return size > oldSize
        val time = time(new).takeIf { it > 0 } ?: Long.MAX_VALUE
        val oldTime = time(old).takeIf { it > 0 } ?: Long.MAX_VALUE
        if (time != oldTime) return time < oldTime
        return new.id < old.id
    }

    fun bands(hash: Long) =
        intArrayOf(
            (hash and 8191).toInt(),
            ((hash ushr 13) and 8191).toInt(),
            ((hash ushr 26) and 8191).toInt(),
            ((hash ushr 39) and 8191).toInt(),
            (hash ushr 52).toInt(),
        )
}
