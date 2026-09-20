package com.example.aicleanphonestorage.feature.battery.data

/** 仅包含公开系统接口返回的小型摘要，不持有 Intent、Context 或图像。null 代表设备没有提供。 */
internal data class BatterySnapshot(
    val percent: Int? = null,
    val charging: Boolean? = null,
    // 电源连接与实际充电分开：达到保护阈值时可以仍插电，但系统报告 NOT_CHARGING。
    val powerConnected: Boolean? = null,
    val brightnessPercent: Int? = null,
    val automaticBrightness: Boolean = false,
    val temperatureTenthsC: Int? = null,
    val voltageMv: Int? = null,
    val technology: String? = null,
    val remainingMah: Long? = null,
    val fullMah: Long? = null,
    val health: BatteryHealth = BatteryHealth.UNKNOWN,
)

internal enum class BatteryHealth {
    GOOD,
    BAD,
    OVERHEATING,
    OVERVOLTAGE,
    COLD,
    UNKNOWN,
}
