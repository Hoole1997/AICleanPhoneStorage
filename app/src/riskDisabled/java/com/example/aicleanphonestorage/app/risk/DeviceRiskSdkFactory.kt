package com.example.aicleanphonestorage.app.risk

import android.app.Application

/** local 关闭风控时不打包产品 SDK，也不生成伪造的检测成功结果。 */
internal fun createDeviceRiskSdk(
    application: Application,
    eventSink: (String, Map<String, *>) -> Unit,
): DeviceRiskSdk = object : DeviceRiskSdk {
    override suspend fun detect(): DeviceRiskSnapshot =
        throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.CONFIGURATION)

    override fun uploadDeviceInfo() = Unit
}
