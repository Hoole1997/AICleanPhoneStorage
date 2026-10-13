package com.example.aicleanphonestorage.app.risk

import android.app.Application

/** 普通 local 构建不打包产品 SDK，也不生成伪造的检测成功结果；联调使用独立 source set。 */
internal fun createDeviceRiskSdk(
    application: Application,
    eventSink: (String, Map<String, *>) -> Unit,
): DeviceRiskSdk = object : DeviceRiskSdk {
    override suspend fun detect(): DeviceRiskSnapshot =
        throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.CONFIGURATION)

    override fun uploadDeviceInfo() = Unit
}
