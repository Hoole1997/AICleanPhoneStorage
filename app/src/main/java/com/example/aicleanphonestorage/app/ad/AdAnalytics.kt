package com.example.aicleanphonestorage.app.ad

import android.app.Application
import net.corekit.core.report.ReportDataManager
import net.corekit.metrics.provider.MetricsModuleProvider

/** 宿主只转发业务事件；SDK 初始化、归因与收益上报统一交给 metrics，避免覆盖模块注册表。 */
internal object AdAnalytics {
    suspend fun initialize(application: Application) {
        MetricsModuleProvider.initialize(application)
        com.example.aicleanphonestorage.core.analytics.BusinessTelemetry.start()
    }

    fun report(name: String, properties: Map<String, Any?>) {
        if (!ReportDataManager.isInitialized()) return
        ReportDataManager.reportData(name, properties.entries.take(25).mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap())
    }
}
