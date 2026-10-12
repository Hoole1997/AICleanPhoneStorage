package com.example.aicleanphonestorage.feature.home.data

import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import org.json.JSONException
import org.json.JSONObject

/**
 * 首页功能入口角标。[key] 同时是远程参数字段名与本地“当日已进入”记录值，发布后不得改名。
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
    BatteryInfo("battery_info");

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

/**
 * Remote Config `home_badge_switch`（JSON 字符串）：字段名为 [HomeBadge.key]，值为 Boolean，
 * 例如 `{"smart_clean":true,"videos":false}`。本地策略是全部显示：字段缺失、类型不符、
 * JSON 无效或参数未下发时都按显示处理，远程只需列出要关闭的入口。
 */
internal object HomeBadgeSwitch {
    const val REMOTE_KEY = "home_badge_switch"
    // 合法配置约 300 字符；异常超长值直接按本地策略处理，不在主流程解析大字符串。
    private const val MAX_LENGTH = 4096

    /** 返回被远程明确关闭的入口。 */
    fun disabled(json: String?): Set<HomeBadge> {
        if (json.isNullOrBlank() || json.length > MAX_LENGTH) return emptySet()
        val values = try { JSONObject(json) } catch (error: JSONException) { return emptySet() }
        // optBoolean 只接受 true/false（含字符串形式），其余类型回落到本地默认值。
        return HomeBadge.entries.filterTo(mutableSetOf()) { !values.optBoolean(it.key, true) }
    }
}

/** 某一天（设备本地时区的 epochDay）真正进入过的功能；跨天后自动失效，不需要清理任务。 */
data class HomeBadgeVisits(val epochDay: Long = NO_DAY, val entered: Set<HomeBadge> = emptySet()) {
    fun enteredOn(day: Long): Set<HomeBadge> = if (epochDay == day) entered else emptySet()

    companion object { const val NO_DAY = Long.MIN_VALUE }
}

/** 默认全部显示，减去远程关闭项与当日已进入的功能；所有输入都是有界小集合。 */
internal fun visibleHomeBadges(
    today: Long,
    disabled: Set<HomeBadge>,
    vararg visits: HomeBadgeVisits,
): Set<HomeBadge> =
    HomeBadge.entries.filterTo(mutableSetOf()) { badge ->
        badge !in disabled && visits.none { badge in it.enteredOn(today) }
    }
