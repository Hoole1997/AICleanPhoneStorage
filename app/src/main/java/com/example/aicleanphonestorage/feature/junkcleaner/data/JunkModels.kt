package com.example.aicleanphonestorage.feature.junkcleaner.data

import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.util.Locale

internal enum class JunkKind(val photos: Boolean = false) {
    INSTALLERS,
    TEMPORARY,
    OLD_LOGS,
    EMPTY_FILES,
    DUPLICATES(true),
    SIMILAR(true),
    REVIEW_QUALITY(true);

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value }
    }
}

internal data class JunkCategorySummary(
    val kind: JunkKind,
    val count: Int = 0,
    val bytes: Long = 0,
    val selected: Int = 0,
)

/** 文件名只是候选依据，不声称 .tmp/.log 一定无用；最近写入的临时数据一律跳过。 */
internal object JunkRules {
    private const val DAY = 86_400_000L

    fun classify(file: ScannedFile, now: Long): JunkKind? {
        val old7 = file.modifiedMillis > 0 && file.modifiedMillis <= now - 7 * DAY
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            file.size == 0L && old7 -> JunkKind.EMPTY_FILES
            file.category == FileCategory.APK && file.size > 0 -> JunkKind.INSTALLERS
            extension in setOf("tmp", "temp", "part", "crdownload") && old7 -> JunkKind.TEMPORARY
            extension == "log" &&
                file.modifiedMillis > 0 &&
                file.modifiedMillis <= now - 30 * DAY -> JunkKind.OLD_LOGS
            else -> null
        }
    }

    fun include(file: ScannedFile, folder: String, now: Long): Boolean {
        if (folder.contains("/AIClean/Compressed", ignoreCase = true)) return false
        return classify(file, now) != null ||
            (file.size > 0 && file.category == FileCategory.PHOTOS)
    }
}

/** 固定尺寸视觉特征。相似仅代表候选，不能证明照片内容相同。 */
internal data class PhotoSignature(
    val hash: Long,
    val ratio: Double,
    val mean: Int,
    val contrast: Double,
    val sharpness: Double,
    val pixels: Long,
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
) {
    fun similar(other: PhotoSignature) =
        contrast >= 12 &&
            other.contrast >= 12 &&
            kotlin.math.abs(red - other.red) < 24 &&
            kotlin.math.abs(green - other.green) < 24 &&
            kotlin.math.abs(blue - other.blue) < 24 &&
            kotlin.math.abs(ratio - other.ratio) < 0.035 &&
            kotlin.math.abs(mean - other.mean) < 16 &&
            java.lang.Long.bitCount(hash xor other.hash) <= 4

    val needsReview: Boolean
        get() = pixels < 300_000 || (sharpness < 22 && contrast < 18)
}

internal object PhotoMetrics {
    fun signature(
        gray: IntArray,
        width: Int,
        height: Int,
        originalWidth: Int,
        originalHeight: Int,
    ): PhotoSignature {
        require(width >= 9 && height >= 8 && gray.size == width * height)
        var hash = 0L
        for (y in 0..7) for (x in 0..7) {
            val row = y * (height - 1) / 7
            val left = x * (width - 1) / 8
            val right = (x + 1) * (width - 1) / 8
            if (gray[row * width + left] > gray[row * width + right])
                hash = hash or (1L shl (y * 8 + x))
        }
        val mean = gray.average()
        var contrast = 0.0
        var lap = 0.0
        var lap2 = 0.0
        var n = 0
        for (v in gray) contrast += (v - mean) * (v - mean)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val d = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]
            lap += d
            lap2 += d.toDouble() * d
            n++
        }
        return PhotoSignature(
            hash,
            originalWidth.toDouble() / originalHeight,
            mean.toInt(),
            kotlin.math.sqrt(contrast / gray.size),
            if (n > 0) (lap2 / n - (lap / n) * (lap / n)).coerceAtLeast(0.0) else 0.0,
            originalWidth.toLong() * originalHeight,
        )
    }
}
