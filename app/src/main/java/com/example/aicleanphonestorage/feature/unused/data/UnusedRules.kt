package com.example.aicleanphonestorage.feature.unused.data

import com.example.aicleanphonestorage.feature.filecleaner.data.*

/** 代理判定，不把修改时间称为打开/使用时间。bucket 与其他功能独立。 */
internal enum class UnusedKind(val wire: String) {
    INSTALLED_APK("installed_apk"), RESIDUE("residue"), DOWNLOAD("download");
    val bucket get() = "unused_$wire"
    companion object { fun from(bucket: String?) = entries.firstOrNull { it.bucket == bucket } }
}

internal data class UnusedGroup(val kind: UnusedKind, val totals: SelectionTotals)

internal object UnusedRules {
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    fun residualOwner(relativePath: String): String? {
        val parts = relativePath.split('/')
        return parts.getOrNull(2)?.takeIf {
            parts.firstOrNull() == "Android" && parts.getOrNull(1) in setOf("data", "obb") && packageName.matches(it)
        }
    }
    fun download(file: ScannedFile, relativePath: String, now: Long): Boolean =
        !file.isDirectory && file.size > 0 && relativePath.substringBefore('/') == "Download" &&
            file.category in setOf(FileCategory.DOCUMENTS, FileCategory.ARCHIVES) &&
            file.modifiedMillis > 0 && file.modifiedMillis < now - 30L * 86_400_000
}
