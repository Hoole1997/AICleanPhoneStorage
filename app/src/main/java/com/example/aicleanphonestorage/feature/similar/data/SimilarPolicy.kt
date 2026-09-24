package com.example.aicleanphonestorage.feature.similar.data

import com.example.aicleanphonestorage.core.media.PhotoSignature
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.util.Locale

/** 相似仅代表供用户复核的候选；哈希召回与画面结构验证分开，不宣称内容相同。 */
internal object SimilarPolicy {
    const val MAX_COMPARISONS = 512
    const val MAX_HASH_DISTANCE = 8
    const val MAX_RATIO_CHANGE = 0.08
    const val MAX_EXPOSURE_CHANGE = 48

    fun matches(a: PhotoSignature, b: PhotoSignature): Boolean {
        val left = a.layout
        val right = b.layout
        // 升级前的临时索引没有结构特征，只允许沿用旧的保守判定。
        if (left?.size != 64 || right?.size != 64) return a.similar(b)
        if (a.contrast < 8 || b.contrast < 8 || a.ratio <= 0 || b.ratio <= 0) return false
        if (kotlin.math.abs(a.ratio - b.ratio) / maxOf(a.ratio, b.ratio) > MAX_RATIO_CHANGE ||
            kotlin.math.abs(a.mean - b.mean) > MAX_EXPOSURE_CHANGE ||
            java.lang.Long.bitCount(a.hash xor b.hash) > MAX_HASH_DISTANCE) return false
        // 去掉整体曝光变化后再比较色偏；不把不同颜色但相同轮廓的图片仅凭哈希合并。
        val colorDifference = listOf(a.red - a.mean - b.red + b.mean,
            a.green - a.mean - b.green + b.mean, a.blue - a.mean - b.blue + b.mean)
        if (colorDifference.any { kotlin.math.abs(it) > 32 } || colorDifference.sumOf { kotlin.math.abs(it) } > 60) return false
        var sumA = 0.0; var sumB = 0.0; var aa = 0.0; var bb = 0.0; var ab = 0.0
        for (i in left.indices) {
            val x = (left[i].toInt() and 255).toDouble()
            val y = (right[i].toInt() and 255).toDouble()
            sumA += x; sumB += y; aa += x*x; bb += y*y; ab += x*y
        }
        val varianceA = aa - sumA*sumA/64
        val varianceB = bb - sumB*sumB/64
        // 常量/近纯色网格不能提供足够画面结构证据，即便 dHash 一样也不自动归组。
        if (varianceA < 64*16 || varianceB < 64*16) return false
        val correlation = (ab - sumA*sumB/64) / kotlin.math.sqrt(varianceA*varianceB)
        return correlation >= 0.95
    }

    /** <=8 位差异分配到五段时，至少一段至多差1位；同段多探测避免放宽阈值却仍漏召回。 */
    fun probes(hash: Long): List<IntArray> = bands(hash).mapIndexed { i, band ->
        val bits = if (i == 4) 12 else 13
        IntArray(bits + 1) { bit -> if (bit == 0) band else band xor (1 shl (bit - 1)) }
    }
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
