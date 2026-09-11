package com.example.aicleanphonestorage.app.ad

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.frequency.AdFrequencyController
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object HotStartAdEligibility {
    private val checks = AtomicLong()

    suspend fun allowed(): Boolean = withContext(Dispatchers.Default) {
        val check = checks.incrementAndGet()
        val sdkState = AdSdkInitializer.state.value
        HotStartAdLog.event("frequency_check_begin check=$check type=APP_OPEN sdkState=$sdkState")
        try {
            // 分别查询总控和每个平台；仅做只读诊断，不增加计数或上报竞价排除事件。
            val total = AdFrequencyController.getTotalFrequencyStatus(AdType.APP_OPEN)
            HotStartAdLog.frequency(check, "TOTAL", total)
            val statuses = AdPlatform.entries.mapNotNull { platform ->
                try {
                    val status = AdFrequencyController.getPlatformFrequencyStatus(AdType.APP_OPEN, platform)
                    HotStartAdLog.frequency(check, platform.name, status)
                    platform to status
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // 单个平台无法查询只跳过该平台，不能阻断其他已经允许竞价的平台。
                    HotStartAdLog.failure("frequency check=$check type=APP_OPEN scope=$platform blockReasonKey=platform_status_unavailable", error)
                    null
                }
            }.toMap()
            val totalAllowed = total.canShow()
            val bidAllowed = statuses.filterValues { it.canBid() }.keys
            val allowed = if (totalAllowed) bidAllowed else emptySet()
            val blockedPlatforms = AdPlatform.entries.filterNot { it in allowed }
            val maxInterval = (statuses.values + total).maxOf { it.remainingIntervalSeconds }
            // 任意平台 canBid 即可；总控 canShow 必须单独满足，不再要求所有平台通过。
            val reason = when {
                sdkState != AdInitializationState.READY -> "sdk_not_ready"
                !isAdSlotEnabled("splash") -> "slot_disabled"
                !totalAllowed -> "total_frequency_blocked"
                !frequencyAllowed(totalAllowed, bidAllowed) -> "no_platform_allowed"
                else -> "none"
            }
            HotStartAdLog.event("frequency_decision check=$check allowed=${reason == "none"} reason=$reason " +
                "sdkState=$sdkState totalAllowed=$totalAllowed bidAllowedPlatforms=$bidAllowed allowedPlatforms=$allowed blockedPlatforms=$blockedPlatforms " +
                "maxRemainingIntervalSeconds=$maxInterval")
            reason == "none"
        } catch (cancelled: CancellationException) {
            HotStartAdLog.event("frequency_check_cancelled check=$check")
            throw cancelled
        } catch (error: Exception) {
            HotStartAdLog.failure("frequency_decision check=$check allowed=false reason=total_status_unavailable sdkState=$sdkState", error)
            false
        }
    }

    fun frequencyAllowed(totalAllowed: Boolean, bidAllowed: Set<AdPlatform>): Boolean =
        totalAllowed && bidAllowed.isNotEmpty()
}
