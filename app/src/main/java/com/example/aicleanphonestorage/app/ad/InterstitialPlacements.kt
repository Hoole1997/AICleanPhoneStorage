package com.example.aicleanphonestorage.app.ad

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 与需求总表同步；只描述触发来源，请求资格、频次和填充继续由 AdExt/SDK 处理。 */
internal object InterstitialPlacements {
    const val NOTIFICATIONS_EXIT = "back_home_notify"
    const val APPS_EXIT = "back_home_apps"
    const val NETWORK_EXIT = "back_home_network"
    // 流量页目前没有确认操作，仅保留总表 Key，不配置或请求此广告位。
    const val NETWORK_CONFIRM = "clean_confirm_network"

    val homeExits: Set<String> = CleanupFeature.entries.mapNotNull(::exit).toSet() +
        setOf(NOTIFICATIONS_EXIT, APPS_EXIT, NETWORK_EXIT)

    fun clean(feature: CleanupFeature?): String? = suffix(feature)?.let { "clean_confirm_$it" }

    /** 功能页退出与完成页退出共用一个 Key；未知来源不生成总表以外的广告位。 */
    fun exit(feature: CleanupFeature?): String? = suffix(feature)?.let { "back_home_$it" }

    private fun suffix(feature: CleanupFeature?): String? = when (feature) {
        CleanupFeature.SMART_CLEAN -> "junk"
        CleanupFeature.SCREENSHOTS -> "screenshots"
        CleanupFeature.PHOTO_COMPRESS -> "photo"
        CleanupFeature.LARGE_FILES -> "large"
        CleanupFeature.UNUSED_FILES -> "unused"
        null -> null
    }
}
