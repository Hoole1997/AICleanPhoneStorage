package com.example.aicleanphonestorage.feature.push

import android.content.Intent
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.MetricEvent

/** 仅常驻入口携带白名单上下文；普通推送不误算，读取后即移除以避免旋转重报。 */
internal object ResidentClickTelemetry {
    const val ENTRY = "metrics.resident.entry"
    const val BADGE = "metrics.resident.clean_badge"
    fun consume(intent: Intent) {
        take(intent)?.let { BusinessTelemetry.emit(MetricEvent.NOTIFBAR_ENTRY_CLICK, it) }
    }

    fun take(intent: Intent): Map<String, Any>? {
        val entry = intent.getStringExtra(ENTRY)
        val badge = intent.getStringExtra(BADGE)
        intent.removeExtra(ENTRY); intent.removeExtra(BADGE)
        if (entry !in setOf("clean", "app", "photos", "accelerate") || badge !in setOf("shown", "hidden", "none")) return null
        // 当前通知没有 App 数量角标，因此不填虚构的 app_badge_count=0；业务缺口记录在覆盖报告。
        return mapOf("entry" to requireNotNull(entry), "clean_badge" to requireNotNull(badge))
    }
}
