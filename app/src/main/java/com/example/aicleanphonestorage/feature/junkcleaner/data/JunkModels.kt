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
    val flaggedApks: Int = 0,
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

internal typealias PhotoSignature = com.example.aicleanphonestorage.core.media.PhotoSignature
internal typealias PhotoMetrics = com.example.aicleanphonestorage.core.media.PhotoMetrics
