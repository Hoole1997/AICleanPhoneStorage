package io.docview.push

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
    fun contentIcon(destination: NotificationDestination): Int
    /** 来源模块的可选业务能力完整保留，由宿主决定是否存在相应页面和真实数据源。 */
    val earthquakeEnabled: Boolean get() = false
    val backgroundServiceEnabled: Boolean get() = false
    val repeatNotificationsEnabled: Boolean get() = false
    fun onEvent(name: String, properties: Map<String, Any?>) {}
}

interface NotificationRuntimeOwner {
    val notificationRuntime: NotificationRuntime
}
