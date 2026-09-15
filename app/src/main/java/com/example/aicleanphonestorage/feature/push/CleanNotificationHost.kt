package com.example.aicleanphonestorage.feature.push

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import io.docview.push.*
import io.docview.push.analytics.NotificationContent

/** 其余入口预留短角标；生产环境 Clean 角标由首页的共享虚拟清理状态提供。 */
internal data class ResidentBadges(
    val clean: String? = null,
    val network: String? = null,
    val photos: String? = null,
    val unusedFiles: String? = null,
)

internal class CleanNotificationHost(
    context: Context,
    private val cleaning: HomeCleaningState? = null,
    private val appCount: () -> Int? = { null },
    private val refreshResident: () -> Unit = {},
) : NotificationHost {
    private val app = context.applicationContext
    @Volatile var badges = ResidentBadges()
        private set

    fun updateBadges(value: ResidentBadges) {
        // 入口即限制长度，避免长文本/文件明细被常驻对象保留。
        fun String?.bounded() = this?.trim()?.take(8)?.takeIf { it.isNotEmpty() }
        badges = ResidentBadges(value.clean.bounded(), value.network.bounded(), value.photos.bounded(), value.unusedFiles.bounded())
    }

    private fun localized(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        })
    }

    override fun onEvent(name: String, properties: Map<String, Any?>) {
        if (name == "Notific_Show") {
            // 与 Click/Enter 共用有界启动队列，SDK 就绪前也保留自家通知展示快照。
            com.example.aicleanphonestorage.core.analytics.BusinessTelemetry.emit(
                com.example.aicleanphonestorage.core.analytics.MetricEvent.NOTIFICATION_SHOW,
                properties.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
            )
        } else com.example.aicleanphonestorage.app.ad.AdAnalytics.report(name, properties)
        // 复用模块的 FCM/定时触发事件。仅“确保存在”不会重建已有通知，这里请求同 ID 内容刷新。
        // 常驻展示上报不是 Notific_Pull，不会形成刷新递归，也不改变 notification 模块逻辑。
        if (name == "Notific_Pull") refreshResident()
    }

    // 用户选择双层屏幕事件监听；不启用 periodicPushEnabled 的旧定时推送。
    override val backgroundServiceEnabled = true

    override val smallIcon = R.drawable.ic_home_clean
    override val appName get() = localized().getString(R.string.app_name)
    override val residentChannelName get() = localized().getString(R.string.push_shortcuts_channel)
    override val pushChannelName get() = localized().getString(R.string.push_reminders_channel)
    override val versionName: String by lazy {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }

    override fun contentIcon(destination: NotificationDestination): Int = when (destination) {
        NotificationDestination.CLEAN -> R.drawable.ic_resident_clean
        NotificationDestination.NETWORK -> R.drawable.ic_tool_network
        NotificationDestination.PHOTOS -> R.drawable.ic_tool_compress
        NotificationDestination.UNUSED_FILES -> R.drawable.ic_tool_unused_files
        NotificationDestination.SCREENSHOTS -> R.drawable.ic_tool_screenshots
        NotificationDestination.HOME -> R.mipmap.ic_launcher
        NotificationDestination.LARGE_FILES -> R.drawable.ic_tool_large_files
        NotificationDestination.NOTIFICATION_CLEANER -> R.drawable.ic_tool_notifications
        NotificationDestination.APP_MANAGER -> R.drawable.ic_tool_apps
    }

    override fun contentIntent(destination: NotificationDestination) =
        NotificationNavigation.pendingIntent(app, destination, residentContent())

    override fun residentContent(): NotificationContent {
        val context = localized()
        return residentFrame(context, installedCount(context)).content
    }

    /** 只格式化共享快照，通知构建（包括主线程 FGS 晋升）不再独立查询 PackageManager。 */
    private fun installedCount(context: Context): String? = appCount()?.let { count ->
        java.text.NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).apply {
            isGroupingUsed = false
        }.format(count)
    }

    override fun residentViews(compact: Boolean): RemoteViews {
        val context = localized()
        return residentViews(compact, context, installedCount(context))
    }

    internal fun residentViews(compact: Boolean, context: Context, installedApps: String? = installedCount(context)): RemoteViews {
        val largeText = context.resources.configuration.fontScale > 1.3f
        val layout = if (compact && largeText) R.layout.notification_shortcuts_accessible
            else if (compact) R.layout.notification_shortcuts_compact else R.layout.notification_shortcuts
        val views = RemoteViews(app.packageName, layout)
        val frame = residentFrame(context, installedApps)
        val items = frame.items
        val paid = frame.paid
        val clean = items.first().badge
        views.setViewVisibility(R.id.shortcut_unused, if (paid) View.VISIBLE else View.GONE)
        for (item in items) {
            val label = context.getString(item.label)
            views.setTextViewText(item.text, label)
            views.setContentDescription(item.root, listOfNotNull(label, item.badge).joinToString(", "))
            val badgeState = when {
                cleaning?.state?.value?.paidUser != true -> "none"
                clean != null -> "shown"
                else -> "hidden"
            }
            views.setOnClickPendingIntent(item.root,
                NotificationNavigation.residentPendingIntent(app, item.destination, item.entry, badgeState, frame.content))
            views.setTextViewText(item.badgeView, item.badge.orEmpty())
            views.setViewVisibility(item.badgeView, if (item.badge == null || (compact && largeText)) View.GONE else View.VISIBLE)
        }
        views.setOnClickPendingIntent(R.id.notification_shortcuts, NotificationNavigation.pendingIntent(app, NotificationDestination.HOME, frame.content))
        return views
    }

    /** 同一可见项模型同时生成 RemoteViews 与埋点文案；自然用户仍隐藏第四个入口。 */
    private fun residentFrame(context: Context, installedApps: String?): ResidentFrame {
        val snapshot = cleaning?.check()
        val paid = snapshot?.paidUser == true
        val clean = (snapshot?.cleanBadge(context.resources.configuration.locales[0]) ?: badges.clean).takeIf { paid }
        val items = listOf(
            Item(R.id.shortcut_clean, R.id.shortcut_clean_label, R.id.shortcut_clean_badge, R.string.push_clean, NotificationDestination.CLEAN, clean, "clean"),
            Item(R.id.shortcut_network, R.id.shortcut_network_label, R.id.shortcut_network_badge, R.string.push_apps, NotificationDestination.APP_MANAGER, installedApps.takeIf { paid }, "app"),
            Item(R.id.shortcut_photos, R.id.shortcut_photos_label, R.id.shortcut_photos_badge, R.string.push_photos, NotificationDestination.SCREENSHOTS, null, "photos"),
            Item(R.id.shortcut_unused, R.id.shortcut_unused_label, R.id.shortcut_unused_badge, R.string.push_accelerate, NotificationDestination.APP_MANAGER, null, "accelerate"),
        )
        val visible = if (paid) items else items.dropLast(1)
        val text = visible.joinToString(" / ") { item ->
            listOfNotNull(context.getString(item.label), item.badge).joinToString(" ")
        }
        return ResidentFrame(paid, items, NotificationContent(context.getString(R.string.app_name), text).bounded())
    }

    private data class ResidentFrame(val paid: Boolean, val items: List<Item>, val content: NotificationContent)

    private data class Item(val root: Int, val text: Int, val badgeView: Int, val label: Int,
        val destination: NotificationDestination, val badge: String?, val entry: String)
}
