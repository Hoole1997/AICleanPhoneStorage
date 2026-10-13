package com.example.aicleanphonestorage.app.risk

/** XM 检测结果的业务维度；策略配置与 SDK 原始模型在各自边界转换。 */
internal enum class DeviceRiskSignal {
    VPN, DNS, IP, SIMULATOR, ADB, NO_SIM, NO_GP, SYS_9_LOW, PLAY,
}

/** 一次检测的有界快照，不保存 SDK 对象、设备标识或 Activity。 */
internal data class DeviceRiskSnapshot(val detectedSignals: Set<DeviceRiskSignal>)
