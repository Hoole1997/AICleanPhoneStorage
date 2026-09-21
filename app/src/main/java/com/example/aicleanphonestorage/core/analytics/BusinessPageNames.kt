package com.example.aicleanphonestorage.core.analytics

/** 只在业务埋点边界转换飞书协议拼写；不更改导航、广告位或广告配置键。 */
internal object BusinessPageNames {
    fun wire(page: String): String = when (page) {
        "video" -> "vedio"
        "video_result" -> "vedion_result"
        "duplicate" -> "duplicatephoto"
        "duplicate_result" -> "duplicatephoto_result"
        "malware" -> "MalwareScan"
        "malware_result" -> "MalwareScan_result"
        "battery" -> "BatteryInfo"
        else -> page
    }
}
