package com.example.aicleanphonestorage.feature.networktraffic.data

import java.time.Clock
import java.time.Duration
import java.time.ZoneId

enum class TrafficPeriod { THIS_MONTH, LAST_MONTH, LAST_24_HOURS }
data class TrafficWindow(val startMillis: Long, val endMillis: Long)

/** 一次请求只读取一次 now，自然月遵守时区，最近24小时使用真实时长（跨夏令时也正确）。 */
class TrafficPeriodResolver(private val clock: Clock = Clock.systemDefaultZone(), private val zone: ZoneId = clock.zone) {
    fun resolve(period: TrafficPeriod): TrafficWindow {
        val now = clock.instant()
        val month = now.atZone(zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
        return when (period) {
            TrafficPeriod.THIS_MONTH -> TrafficWindow(month.toInstant().toEpochMilli(), now.toEpochMilli())
            TrafficPeriod.LAST_MONTH -> TrafficWindow(month.minusMonths(1).toInstant().toEpochMilli(), month.toInstant().toEpochMilli())
            TrafficPeriod.LAST_24_HOURS -> TrafficWindow(now.minus(Duration.ofHours(24)).toEpochMilli(), now.toEpochMilli())
        }
    }
}

enum class UsageAvailability { AVAILABLE, PHONE_PERMISSION_REQUIRED, NO_SIM, UNAVAILABLE }
data class NetworkUsage(val bytes: Long?, val availability: UsageAvailability) {
    companion object { fun unavailable(reason: UsageAvailability) = NetworkUsage(null, reason) }
}
data class AppIdentity(val packageName: String, val label: String)
data class TrafficApp(val uid: Int, val packages: List<AppIdentity>, val mobileBytes: Long, val wifiBytes: Long) {
    val bytes: Long get() = addBytes(mobileBytes, wifiBytes)
}
data class TrafficSnapshot(
    val period: TrafficPeriod,
    val window: TrafficWindow,
    val mobile: NetworkUsage,
    val wifi: NetworkUsage,
    val apps: List<TrafficApp>,
) {
    val totalBytes: Long get() = addBytes(mobile.bytes ?: 0, wifi.bytes ?: 0)
}
enum class TrafficStage { MOBILE, WIFI, APPLICATIONS }
data class TrafficProgress(val stage: TrafficStage, val completed: Int? = null, val total: Int? = null) {
    val percent: Int? get() = if (completed != null && total != null && total > 0)
        (completed.toLong() * 100 / total).toInt().coerceIn(0, 100) else null
}

/** 平台异常值不允许形成负流量；加法饱和，避免 Long 溢出后变成负数。 */
internal fun addBytes(first: Long, second: Long): Long {
    val a = first.coerceAtLeast(0)
    val b = second.coerceAtLeast(0)
    return if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}
