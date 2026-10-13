package com.example.aicleanphonestorage.app.risk

import android.app.Application
import com.example.aicleanphonestorage.BuildConfig

/**
 * 只合入正式 Google 和独立 localRiskDebug 的产品适配器，普通 local 不编译此目录。
 *
 * 地址未配置时先返回不可用实现，避免访问 native 类、获取指纹或上传设备数据。
 * 所有 SDK 操作仅接收 Application，不保留启动页和其他 Activity。
 */
internal fun createDeviceRiskSdk(
    application: Application,
    eventSink: (String, Map<String, *>) -> Unit,
): DeviceRiskSdk {
    val baseUrl = BuildConfig.XM_RISK_BASE_URL.trim()
    if (!BuildConfig.XM_RISK_ENABLED || baseUrl.isEmpty()) return UnconfiguredDeviceRiskSdk
    return XmDeviceRiskSdk(application, baseUrl, eventSink)
}

/** 未取得当前产品服务地址时明确失败放行，不能套用其他产品或联调域名。 */
private object UnconfiguredDeviceRiskSdk : DeviceRiskSdk {
    override suspend fun detect(): DeviceRiskSnapshot = unavailable()

    override fun uploadDeviceInfo(): Unit = unavailable()

    private fun unavailable(): Nothing =
        throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.CONFIGURATION)
}
