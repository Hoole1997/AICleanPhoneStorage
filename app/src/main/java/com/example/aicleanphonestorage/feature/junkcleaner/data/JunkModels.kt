package com.example.aicleanphonestorage.feature.junkcleaner.data

import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.util.Locale

internal enum class JunkKind(val photos: Boolean = false) {
    INSTALLERS,
    TEMPORARY,
    OLD_LOGS,
    EMPTY_FILES,
    EMPTY_FOLDERS,
    AD_FILES,
    DUPLICATES(true),
    SIMILAR(true),
    REVIEW_QUALITY(true);

    companion object {
        // 旧分类/照片分析继续保留；入口、摘要、默认选择共用这份可见分类定义。
        val visible = listOf(INSTALLERS, TEMPORARY, EMPTY_FOLDERS, AD_FILES)
        fun from(value: String?) = entries.firstOrNull { it.name == value }
    }
}

internal data class JunkCategorySummary(
    val kind: JunkKind,
    val count: Int = 0,
    val bytes: Long = 0,
    val selected: Int = 0,
)

/** 分类只产生候选；只处理公开共享存储或用户授权目录，不能访问其他应用私有缓存。 */
internal object JunkRules {
    private val temporaryExtensions = setOf("tmp", "temp", "log", "part", "crdownload")
    private val adTokens = setOf("ad", "ads", "advert", "advertisement", "advertising")
    private val adSdkTokens = setOf(
        "admob", "applovin", "vungle", "pangle", "bytedanceads", "unityads", "ironsource",
        "mbridge", "mintegral", "chartboost", "inmobi", "tapjoy", "adcolony", "baiduads",
        "gdtads", "ttad", "ttads", "tt_ad", "com.google.android.gms.ads",
    )
    private val tokenSeparator = Regex("[^a-z0-9]+")
    private val camelBoundary = Regex("([a-z0-9])([A-Z])")

    @Suppress("UNUSED_PARAMETER") // 新规则不再按文件年龄过滤，保留旧调用签名。
    fun classify(file: ScannedFile, now: Long, folder: String = file.path.substringBeforeLast('/', "")): JunkKind? {
        if (file.isDirectory) return JunkKind.EMPTY_FOLDERS // 仅由确认整棵子树无文件的扫描器发出。
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            extension == "apk" -> JunkKind.INSTALLERS
            hasAdMarker("$folder/${file.name}") -> JunkKind.AD_FILES
            extension in temporaryExtensions || pathTokens(folder).any { it == "cache" || it == "caches" } -> JunkKind.TEMPORARY
            else -> null
        }
    }

    private fun pathTokens(path: String): List<String> =
        camelBoundary.replace(path, "$1/$2").lowercase(Locale.ROOT).split(tokenSeparator)

    internal fun hasAdMarker(path: String): Boolean {
        val segments = path.lowercase(Locale.ROOT).split('/')
        return pathTokens(path).any { it in adTokens || it in adSdkTokens } ||
            segments.any { segment -> adSdkTokens.any { sdk -> segment == sdk || segment.startsWith("$sdk.") || segment.startsWith(".$sdk") } }
    }

    fun include(file: ScannedFile, folder: String, now: Long): Boolean {
        if (folder.contains("/AIClean/Compressed", ignoreCase = true)) return false
        return classify(file, now, folder) != null
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
