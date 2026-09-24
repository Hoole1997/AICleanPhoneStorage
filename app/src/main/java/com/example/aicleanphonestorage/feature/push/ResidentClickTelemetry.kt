package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.MetricEvent

/** 仅常驻入口携带白名单上下文；普通推送不误算，读取后即移除以避免旋转重报。 */
internal object ResidentClickTelemetry {
    const val ENTRY = "metrics.resident.entry"
    const val BADGE = "metrics.resident.clean_badge"
    const val APP_BADGE_COUNT = "metrics.resident.app_badge_count"

    fun consume(intent: Intent) {
        take(intent)?.let { BusinessTelemetry.emit(MetricEvent.NOTIFBAR_ENTRY_CLICK, it) }
    }

    fun take(intent: Intent): Map<String, Any>? {
        val entry = intent.getStringExtra(ENTRY)
        val badge = intent.getStringExtra(BADGE)
        val appBadgeCount = intent.getIntExtra(APP_BADGE_COUNT, -1).takeIf { it >= 0 }
        intent.removeExtra(ENTRY); intent.removeExtra(BADGE); intent.removeExtra(APP_BADGE_COUNT)
        if (entry !in setOf("clean", "app", "photos", "accelerate") || badge !in setOf("shown", "hidden", "none")) return null
        // 旧通知或共享快照尚未就绪时保留点击事件，但不把未知数量伪装成 0。
        return buildMap {
            put("entry", requireNotNull(entry))
            put("clean_badge", requireNotNull(badge))
            appBadgeCount?.let { put("app_badge_count", it) }
        }
    }
}
