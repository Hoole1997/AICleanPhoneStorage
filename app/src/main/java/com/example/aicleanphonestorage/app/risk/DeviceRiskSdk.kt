package com.example.aicleanphonestorage.app.risk

/** 产品 AAR 只合入 Google 和独立 localRiskDebug；业务编排不依赖混淆类名或 SDK 结果实体。 */
internal interface DeviceRiskSdk {
    suspend fun detect(): DeviceRiskSnapshot
    fun uploadDeviceInfo()
}

internal enum class DeviceRiskUnavailableReason {
    CONFIGURATION, SDK_ERROR, FINGERPRINT, TIMEOUT, POLICY, ANALYTICS, CANCELLED,
}

/** 只携带有界原因，不把 SDK 的指纹、token、deviceId 或服务端原文交给日志/页面。 */
internal class DeviceRiskUnavailableException(val reason: DeviceRiskUnavailableReason) :
    Exception(reason.name)
