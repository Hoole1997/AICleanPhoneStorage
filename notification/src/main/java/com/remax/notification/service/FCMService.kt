package com.remax.notification.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.remax.notification.NotificationRuntimeOwner
import kotlinx.coroutines.runBlocking

/** 提取原 FCMService，移除 AppUtils、统计、保活依赖，恢复真正生效的 version 过滤。 */
class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val runtime = (application as? NotificationRuntimeOwner)?.notificationRuntime ?: return
        // Firebase 在工作线程调用此方法。仅完成本地通知短事务，不下载图片或发起网络请求。
        runBlocking { runtime.receive(message.data, message.messageId) }
    }

    override fun onNewToken(token: String) {
        (application as? NotificationRuntimeOwner)?.notificationRuntime?.onTokenChanged()
    }
}
