package com.example.aicleanphonestorage.app.ad

import android.app.Application
import net.corekit.core.report.ReportDataManager
import net.corekit.metrics.provider.MetricsModuleProvider
import kotlinx.coroutines.flow.asStateFlow

/** 宿主只转发业务事件；SDK 初始化、归因与收益上报统一交给 metrics，避免覆盖模块注册表。 */
internal object AdAnalytics {
    private val initialized = kotlinx.coroutines.flow.MutableStateFlow(false)
    val ready = initialized.asStateFlow()
    suspend fun initialize(application: Application) {
        MetricsModuleProvider.initialize(application)
        com.example.aicleanphonestorage.core.analytics.BusinessTelemetry.start()
        initialized.value = ReportDataManager.isInitialized()
    }

    // 只在 initialize 返回后读取；那时模块已注册 Reporter 和公共参数，不另建数数实例。
    fun isThinkingDataReady() = net.corekit.metrics.data.ThinkingReporter.checkInitialized()

    fun report(name: String, properties: Map<String, Any?>) {
        if (!ReportDataManager.isInitialized()) return
        ReportDataManager.reportData(name, properties.entries.take(25).mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap())
    }

    /** 返回是否已交给统一接口；未初始化时不能提前消费 Grouping 的一次性记录。 */
    fun reportGrouping(name: String): Boolean {
        if (!initialized.value || !ReportDataManager.isInitialized()) return false
        ReportDataManager.reportData(name, emptyMap())
        return true
    }
}
