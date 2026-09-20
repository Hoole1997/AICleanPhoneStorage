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
    /** 普通通知构建时读取 APP 当前语言，不缓存初始化时的语言。 */
    val contentLanguageTag: String get() = "en"
    /** 冷启动先恢复 APP 语言，防止后台首条通知抢在语言偏好恢复之前生成。 */
    suspend fun awaitContentLanguage() {}
    fun contentIntent(destination: NotificationDestination): PendingIntent
    fun residentViews(compact: Boolean): RemoteViews
    fun residentContent() = io.docview.push.analytics.NotificationContent(appName, "")
    fun contentIcon(destination: NotificationDestination): Int
    /** 来源模块的可选业务能力完整保留，由宿主决定是否存在相应页面和真实数据源。 */
    val earthquakeEnabled: Boolean get() = false
    /** 事件监听前台服务，复用用户可见常驻通知；与旧周期任务独立。 */
    val backgroundServiceEnabled: Boolean get() = false
    val periodicPushEnabled: Boolean get() = false
    val repeatNotificationsEnabled: Boolean get() = false
    fun onEvent(name: String, properties: Map<String, Any?>) {}
}

interface NotificationRuntimeOwner {
    val notificationRuntime: NotificationRuntime
}
