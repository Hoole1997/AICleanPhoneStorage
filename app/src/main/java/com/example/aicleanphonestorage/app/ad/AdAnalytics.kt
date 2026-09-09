package com.example.aicleanphonestorage.app.ad

import android.app.Application
import android.os.Bundle
import cn.thinkingdata.analytics.TDAnalytics
import com.example.aicleanphonestorage.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueAdReporter
import net.corekit.core.report.ReportDataManager
import net.corekit.core.report.ReporterData
import org.json.JSONObject

/** 沿用来源 core 的 ReporterData/RevenueAdReporter 接口，不引入地图或 Launcher 的混淆 Application。 */
internal object AdAnalytics {
    suspend fun initialize(application: Application) {
        val reporters = mutableListOf<ReporterData>()
        val revenue = mutableListOf<RevenueAdReporter>()
        if (FirebaseApp.getApps(application).isNotEmpty()) {
            FirebaseReporter(FirebaseAnalytics.getInstance(application)).also {
                reporters += it
                revenue += it
            }
        }
        if (BuildConfig.THINKING_DATA_APP_ID.isNotBlank() && BuildConfig.THINKING_DATA_SERVER_URL.isNotBlank()) {
            val initialized = withContext(Dispatchers.IO) {
                try {
                    TDAnalytics.init(application, BuildConfig.THINKING_DATA_APP_ID, BuildConfig.THINKING_DATA_SERVER_URL)
                    TDAnalytics.enableLog(false)
                    true
                } catch (_: Exception) { false }
            }
            if (initialized) reporters += ThinkingReporter()
        }
        // 初始化完后一次性发布注册表，避免广告回调遍历期间反复替换列表。
        ReportDataManager.setReporters(reporters)
        RevenueAdManager.setReporters(revenue)
        ReportDataManager.setCommonParams(mapOf("distribution" to BuildConfig.FLAVOR, "build_type" to BuildConfig.BUILD_TYPE))
    }

    fun report(name: String, properties: Map<String, Any?>) {
        if (!ReportDataManager.isInitialized()) return
        ReportDataManager.reportData(name, properties.entries.take(25).mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap())
    }

    private fun bounded(data: Map<String, Any>) = data.entries.take(25).associate { (key, value) ->
        key.take(40) to when (value) {
            is Number, is Boolean -> value
            else -> value.toString().take(100)
        }
    }

    private fun bundle(data: Map<String, Any>) = Bundle().apply {
        bounded(data).forEach { (key, value) ->
            when (value) {
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Float -> putDouble(key, value.toDouble())
                is Double -> putDouble(key, value)
                is Boolean -> putLong(key, if (value) 1 else 0)
                else -> putString(key, value.toString())
            }
        }
    }

    private class FirebaseReporter(private val analytics: FirebaseAnalytics) : ReporterData, RevenueAdReporter {
        override fun getName() = "Firebase"
        override fun reportData(eventName: String, data: Map<String, Any>) { analytics.logEvent(eventName, bundle(data)) }
        override fun setCommonParams(params: Map<String, Any>) { analytics.setDefaultEventParameters(bundle(params)) }
        override fun setUserParams(params: Map<String, Any>) {
            bounded(params).forEach { (key, value) -> analytics.setUserProperty(key.take(24), value.toString().take(36)) }
        }
        override fun reportAdRevenue(adRevenueData: RevenueAdData) {
            if (!adRevenueData.revenue.value.isFinite()) return
            analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle(mapOf(
                FirebaseAnalytics.Param.AD_PLATFORM to adRevenueData.platform,
                FirebaseAnalytics.Param.AD_SOURCE to adRevenueData.adRevenueNetwork,
                FirebaseAnalytics.Param.AD_FORMAT to adRevenueData.adFormat,
                FirebaseAnalytics.Param.AD_UNIT_NAME to adRevenueData.adRevenueUnit,
                FirebaseAnalytics.Param.VALUE to adRevenueData.revenue.value,
                FirebaseAnalytics.Param.CURRENCY to adRevenueData.revenue.currencyCode,
            )))
        }
    }

    private class ThinkingReporter : ReporterData {
        override fun getName() = "ThinkingData"
        override fun reportData(eventName: String, data: Map<String, Any>) { TDAnalytics.track(eventName, JSONObject(bounded(data))) }
        override fun setCommonParams(params: Map<String, Any>) { TDAnalytics.setSuperProperties(JSONObject(bounded(params))) }
        override fun setUserParams(params: Map<String, Any>) { TDAnalytics.userSet(JSONObject(bounded(params))) }
    }
}
