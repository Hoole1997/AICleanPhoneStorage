package com.example.aicleanphonestorage.feature.notifications.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只发布连接状态，不保存 Service/Activity 引用。系统授权与实际服务连接是两个不同状态。 */
class NotificationListenerConnection {
    private val current = MutableStateFlow(false)
    val connected = current.asStateFlow()
    fun update(value: Boolean) { current.value = value }
}
