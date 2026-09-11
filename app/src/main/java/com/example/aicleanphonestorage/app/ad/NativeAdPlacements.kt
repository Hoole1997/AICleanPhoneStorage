package com.example.aicleanphonestorage.app.ad

import com.example.aicleanphonestorage.core.ui.completion.CompletionKind
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 原生 Slot Key 与需求总表一致；APP/NETWORK 的完成页位预留，不新增不存在的业务页面。 */
internal enum class NativeAdFeature(val suffix: String) {
    JUNK("junk"),
    SCREENSHOTS("screenshots"),
    PHOTO("photo"),
    LARGE("large"),
    UNUSED("unused"),
    NOTIFY("notify"),
    APPS("apps"),
    NETWORK("network");

    val featureSlot
        get() = "native_feature_$suffix"

    val resultSlot
        get() = "native_result_$suffix"
}

internal object NativeAdPlacements {
    const val HOME = "native_home"
    const val SCAN_DIALOG = "native_scanning"

    fun feature(feature: CleanupFeature): NativeAdFeature =
        when (feature) {
            CleanupFeature.SMART_CLEAN -> NativeAdFeature.JUNK
            CleanupFeature.SCREENSHOTS -> NativeAdFeature.SCREENSHOTS
            CleanupFeature.PHOTO_COMPRESS -> NativeAdFeature.PHOTO
            CleanupFeature.LARGE_FILES -> NativeAdFeature.LARGE
            CleanupFeature.UNUSED_FILES -> NativeAdFeature.UNUSED
        }

    fun result(kind: CompletionKind, source: String?): String? {
        if (kind == CompletionKind.NOTIFICATIONS) return NativeAdFeature.NOTIFY.resultSlot
        val feature = CleanupFeature.entries.firstOrNull { it.name == source }
        if (feature != null) return feature(feature).resultSlot
        // 兼容已有压缩结果；通用清理缺少来源时不猜成垃圾清理，避免错记广告位。
        return if (kind == CompletionKind.COMPRESSION) NativeAdFeature.PHOTO.resultSlot else null
    }
}
