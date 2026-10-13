package com.example.aicleanphonestorage.app.risk

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.corekit.core.ads.RevenueAdData

/**
 * Collect 协议要求的进程内广告事件广播。只持有 Application Context；收益 Reporter
 * 仍由 metrics 独立管理，这里不向 Adjust/Firebase 再次上报同一笔收益。
 */
internal class CollectAdEventBroadcaster(context: Context) {
    private val applicationContext = context.applicationContext

    fun sendImpression(data: RevenueAdData) = send(ACTION_AD_IMPRESSION, data)

    fun sendClick(data: RevenueAdData) = send(ACTION_AD_CLICK, data)

    private fun send(action: String, data: RevenueAdData) {
        try {
            val intent = Intent(action).apply {
                putExtra("revenue", data.revenue.value)
                putExtra("adType", data.adFormat)
                putExtra("platformName", data.platform)
                putExtra("adUnitId", data.adRevenueUnit)
                putExtra("networkName", data.adRevenueNetwork)
                // Bill 对拿不到聚合广告源 ID 的情况已回退为广告位 ID。
                putExtra("networkPlacementId", data.adSourceId)
            }
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        } catch (_: Exception) {
            // 广告回调内仅轻量发送本地广播；失败不能冒泡，也不记录 SDK 数据明细。
            try { Log.w("CollectAdEvents", "Collect ad event delivery failed: $action") }
            catch (_: Exception) { /* 日志失败也不影响广告回调。 */ }
        }
    }

    companion object {
        const val ACTION_AD_IMPRESSION = "android.intent.action.adImpression"
        const val ACTION_AD_CLICK = "android.intent.action.adClick"
    }
}
