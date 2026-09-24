package com.example.aicleanphonestorage.core.media

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
    val layout: ByteArray? = null,
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
        stableSampling: Boolean = false,
    ): PhotoSignature {
        require(width >= 9 && height >= 8 && gray.size == width * height)
        // 独立相似照片入口使用区域均值，避免单点采样被压缩噪声/轻微位移翻转。
        // 旧垃圾照片分析保持原算法；64 字节结构特征只用于独立入口的二次验证。
        val sampled = if (stableSampling) averageGrid(gray, width, height, 9, 8) else null
        var hash = 0L
        for (y in 0..7) for (x in 0..7) {
            val row = y * (height - 1) / 7
            val left = x * (width - 1) / 8
            val right = (x + 1) * (width - 1) / 8
            if (if (sampled != null) sampled[y * 9 + x] > sampled[y * 9 + x + 1]
                else gray[row * width + left] > gray[row * width + right])
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
            layout = if (stableSampling) averageGrid(gray, width, height, 8, 8).map { it.toByte() }.toByteArray() else null,
        )
    }
    private fun averageGrid(gray: IntArray, width: Int, height: Int, columns: Int, rows: Int): IntArray =
        IntArray(columns * rows) { cell ->
            val x0 = (cell % columns) * width / columns
            val x1 = (cell % columns + 1) * width / columns
            val y0 = (cell / columns) * height / rows
            val y1 = (cell / columns + 1) * height / rows
            var total = 0
            for (y in y0 until y1) for (x in x0 until x1) total += gray[y * width + x]
            total / ((x1 - x0) * (y1 - y0))
        }

}
