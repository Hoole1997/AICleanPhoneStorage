package com.example.aicleanphonestorage.app.ad

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/** 广告位仅描述触发位置；请求、频次和展示资格继续由 AdExt/SDK 处理。 */
internal object InterstitialPlacements {
    const val NOTIFICATIONS_EXIT = "notifications_exit_interstitial"
    const val APPS_EXIT = "app_manager_exit_interstitial"
    const val NETWORK_EXIT = "network_traffic_exit_interstitial"
    const val NOTIFICATIONS_COMPLETE_EXIT = "notifications_complete_exit_interstitial"

    fun clean(feature: CleanupFeature?) = "${prefix(feature)}_clean_interstitial"
    fun exit(feature: CleanupFeature?) = "${prefix(feature)}_exit_interstitial"
    fun completionExit(feature: CleanupFeature?) = "${prefix(feature)}_complete_exit_interstitial"

    private fun prefix(feature: CleanupFeature?) = when (feature) {
        CleanupFeature.SMART_CLEAN -> "junk"
        CleanupFeature.SCREENSHOTS -> "screenshots"
        CleanupFeature.PHOTO_COMPRESS -> "photo_compress"
        CleanupFeature.LARGE_FILES -> "large_files"
        CleanupFeature.UNUSED_FILES -> "unused_files"
        null -> "file_cleanup"
    }
}
