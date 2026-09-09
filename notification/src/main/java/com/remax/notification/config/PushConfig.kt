package com.remax.notification.config

/** 提取自 ReMax notification 的配置协议；保留频次/冷却/免打扰，删除保活与重复弹出字段。 */
data class PushConfig(
    val totalPushCount: Int = 3,
    val unlockPushInterval: Int = 10,
    val backgroundPushInterval: Int = 10,
    val newUserCooldown: Int = 24,
    val doNotDisturbStart: String = "02:00",
    val doNotDisturbEnd: String = "08:00",
    val notificationEnabled: Boolean = true,
)
