package com.example.aicleanphonestorage.feature.home.data

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

/**
 * 首页功能入口角标。[key] 是本地“当日已进入”记录值，发布后不得改名。
 * 角标样式（感叹号/红点）属于设计稿，由 UI 层决定；数据层只关心是否显示。
 */
enum class HomeBadge(val key: String) {
    SmartClean("smart_clean"),
    MalwareScan("malware_scan"),
    NetworkTraffic("network_traffic"),
    ClearNotifications("clear_notifications"),
    AppManager("app_manager"),
    PhotoCompress("photo_compress"),
    LargeFiles("large_files"),
    UnusedFiles("unused_files"),
    Screenshots("screenshots"),
    Videos("videos"),
    SimilarPhotos("similar_photos"),
    BatteryInfo("battery_info"),
    SpeedTest("speed_test");

    companion object {
        fun fromKey(key: String): HomeBadge? = entries.firstOrNull { it.key == key }

        /** 共用扫描流程的入口；APK 风险清理也进入 Smart Clean，计为其当日已进入。 */
        fun of(feature: CleanupFeature): HomeBadge = when (feature) {
            CleanupFeature.SMART_CLEAN -> SmartClean
            CleanupFeature.PHOTO_COMPRESS -> PhotoCompress
            CleanupFeature.LARGE_FILES -> LargeFiles
            CleanupFeature.UNUSED_FILES -> UnusedFiles
            CleanupFeature.SCREENSHOTS -> Screenshots
            CleanupFeature.VIDEOS -> Videos
            CleanupFeature.SIMILAR_PHOTOS -> SimilarPhotos
        }
    }
}

/** 某一天（设备本地时区的 epochDay）真正进入过的功能；跨天后自动失效，不需要清理任务。 */
data class HomeBadgeVisits(val epochDay: Long = NO_DAY, val entered: Set<HomeBadge> = emptySet()) {
    fun enteredOn(day: Long): Set<HomeBadge> = if (epochDay == day) entered else emptySet()

    companion object { const val NO_DAY = Long.MIN_VALUE }
}

/** 统一开关关闭时全部隐藏；开启时全部显示，减去当日已进入的功能。所有输入都是有界小集合。 */
internal fun visibleHomeBadges(
    today: Long,
    enabled: Boolean,
    vararg visits: HomeBadgeVisits,
): Set<HomeBadge> =
    if (!enabled) emptySet()
    else HomeBadge.entries.filterTo(mutableSetOf()) { badge -> visits.none { badge in it.enteredOn(today) } }
