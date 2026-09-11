package com.example.aicleanphonestorage.app.ad

import android.util.Log
import com.android.common.bill.ads.frequency.AdFrequencyStatus

/** 热启动诊断统一 Tag；仅在生命周期事件/限频查询时输出，不轮询、不记录通知或广告 ID。 */
internal object HotStartAdLog {
    const val TAG = "HotStartAds"

    fun event(message: String) { Log.i(TAG, message) }
    fun failure(message: String, error: Throwable) { Log.w(TAG, message, error) }

    fun frequency(check: Long, scope: String, status: AdFrequencyStatus) {
        event("frequency check=$check type=APP_OPEN scope=$scope " +
            "isAllowed=${status.isAllowed} canShow=${status.canShow()} canBid=${status.canBid()} " +
            "blockReasonKey=${status.blockReasonKey} " +
            "dailyShowCount=${status.dailyShowCount} maxDailyShow=${status.maxDailyShow} remainingShowCount=${status.remainingShowCount} " +
            "dailyClickCount=${status.dailyClickCount} maxDailyClick=${status.maxDailyClick} remainingClickCount=${status.remainingClickCount} " +
            "lastShowIntervalSeconds=${status.lastShowIntervalSeconds} minIntervalSeconds=${status.minIntervalSeconds} remainingIntervalSeconds=${status.remainingIntervalSeconds}")
    }
}
