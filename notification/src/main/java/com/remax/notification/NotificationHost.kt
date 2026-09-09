package com.remax.notification

import android.app.PendingIntent
import android.widget.RemoteViews

/** 宿主只提供通知展示数据与显式 Activity PendingIntent，模块不依赖业务页面。 */
interface NotificationHost {
    val smallIcon: Int
    val appName: String
    val residentChannelName: String
    val pushChannelName: String
    val versionName: String
    fun contentIntent(destination: NotificationDestination): PendingIntent
    fun residentViews(compact: Boolean): RemoteViews
    fun defaultContent(): PushMessage
}

interface NotificationRuntimeOwner {
    val notificationRuntime: NotificationRuntime
}

enum class NotificationDestination(val key: String) {
    HOME("home"), CLEAN("clean"), NETWORK("network"), PHOTOS("photos"),
    UNUSED_FILES("unused_files"), SCREENSHOTS("screenshots");

    companion object {
        // 消息只可选择预定义入口，不能注入外部 Intent、URI 或组件名称。
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: HOME
    }
}

data class PushMessage(
    val title: String,
    val body: String,
    val destination: NotificationDestination = NotificationDestination.HOME,
    val messageId: String? = null,
)
