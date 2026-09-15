package com.example.aicleanphonestorage.feature.filecleaner.data

import java.util.Locale

/** 四个入口的差异由策略枚举和筛选描述，不复制扫描器或文件操作实现。 */
enum class CleanupFeature {
    PHOTO_COMPRESS,
    LARGE_FILES,
    UNUSED_FILES,
    SCREENSHOTS,
    SMART_CLEAN,
}

enum class FileCategory {
    ALL,
    PHOTOS,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    ARCHIVES,
    APK,
    OTHER,
}

enum class FileBackend {
    MEDIA,
    DIRECT,
    DOCUMENT,
}

data class CleanupFilter(
    val category: FileCategory = FileCategory.ALL,
    val minimumBytes: Long = 10_000_000,
    val recentDays: Int = 0,
    val unusedDays: Int = 30,
    val referenceMillis: Long = System.currentTimeMillis(),
    val bucket: String? = null,
)

data class ScannedFile(
    val id: Long = 0,
    val uri: String,
    val name: String,
    val mime: String,
    val size: Long,
    val modifiedMillis: Long,
    val category: FileCategory,
    val backend: FileBackend,
    val scope: String,
    val path: String = "",
    val selected: Boolean = false,
    val quality: Int = 75,
    val bucket: String = "",
    val groupKey: String = "",
    val retained: Boolean = false,
) {
    // 使用索引已有 MIME 字段区分目录，不把空文件伪装成空目录。
    val isDirectory: Boolean get() = mime == "vnd.android.document/directory"
}

data class ScanHandle(
    val id: Long,
    val feature: CleanupFeature,
    val scannedCount: Int,
    val scopeLabel: String,
    val partial: Boolean = false,
    val analysisSkipped: Int = 0,
)

data class SelectionTotals(
    val count: Int = 0,
    val bytes: Long = 0,
    val selectedCount: Int = 0,
    val selectedBytes: Long = 0,
)

data class PreparedOperation(val id: Long, val count: Int, val bytes: Long)

data class ScanProgress(
    val completed: Int,
    val total: Int? = null,
    val stage: String = "FILES",
    val junkBytes: Long? = null,
)

internal object CleanupPolicy {
    private val documents =
        setOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "rtf")
    private val archives = setOf("zip", "rar", "7z", "gz", "tar", "bz2")

    fun category(name: String, mime: String): FileCategory {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") -> FileCategory.PHOTOS
            mime.startsWith("video/") -> FileCategory.VIDEOS
            mime.startsWith("audio/") -> FileCategory.AUDIO
            ext == "apk" || mime == "application/vnd.android.package-archive" -> FileCategory.APK
            ext in archives -> FileCategory.ARCHIVES
            ext in documents || mime.startsWith("text/") -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }

    fun screenshot(name: String, folder: String): Boolean {
        val text = "$folder/$name".lowercase(Locale.ROOT)
        return listOf("screenshot", "screen_shot", "screen shot", "截屏", "截图").any(text::contains)
    }

    fun candidate(feature: CleanupFeature, file: ScannedFile, folder: String, now: Long): Boolean {
        if (feature == CleanupFeature.SMART_CLEAN)
            return com.example.aicleanphonestorage.feature.junkcleaner.data.JunkRules.include(
                file,
                folder,
                now,
            )
        if (file.size <= 0 || folder.contains("/AIClean/Compressed", ignoreCase = true))
            return false
        return when (feature) {
            CleanupFeature.SMART_CLEAN -> false // 已在上方交给垃圾候选策略。
            CleanupFeature.PHOTO_COMPRESS ->
                file.mime in setOf("image/jpeg", "image/png") && file.size >= 100_000
            CleanupFeature.SCREENSHOTS ->
                file.category == FileCategory.PHOTOS && screenshot(file.name, folder)
            CleanupFeature.LARGE_FILES -> file.size >= 10_000_000
            CleanupFeature.UNUSED_FILES ->
                file.modifiedMillis > 0 && file.modifiedMillis <= now - 30L * 86_400_000
        }
    }

}

/** 持久操作快照的大小统计。输入含失败/跳过项；成功原图和输出只统计已验证副本。 */
internal data class OperationStorage(
    val freedBytes: Long,
    val reducedBytes: Long,
    val inputBytes: Long,
    val copiedOriginalBytes: Long,
    val outputBytes: Long,
)
