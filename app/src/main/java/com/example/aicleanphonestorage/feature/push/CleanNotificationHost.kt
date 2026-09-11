package com.example.aicleanphonestorage.feature.push

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import io.docview.push.*

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
        com.example.aicleanphonestorage.app.ad.AdAnalytics.report(name, properties)
        // 复用模块的 FCM/定时触发事件。仅“确保存在”不会重建已有通知，这里请求同 ID 内容刷新。
        // 常驻展示上报不是 Notific_Pull，不会形成刷新递归，也不改变 notification 模块逻辑。
        if (name == "Notific_Pull") refreshResident()
    }

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

    override fun contentIntent(destination: NotificationDestination) = NotificationNavigation.pendingIntent(app, destination)

    override fun residentViews(compact: Boolean): RemoteViews = residentViews(compact, localized())

    internal fun residentViews(compact: Boolean, context: Context): RemoteViews {
        val largeText = context.resources.configuration.fontScale > 1.3f
        val layout = if (compact && largeText) R.layout.notification_shortcuts_accessible
            else if (compact) R.layout.notification_shortcuts_compact else R.layout.notification_shortcuts
        val views = RemoteViews(app.packageName, layout)
        val values = badges
        val clean = if (cleaning == null) values.clean
            else cleaning.check().cleanBadge(context.resources.configuration.locales[0])
        val items = listOf(
            Item(R.id.shortcut_clean, R.id.shortcut_clean_label, R.id.shortcut_clean_badge, R.string.push_clean, NotificationDestination.CLEAN, clean),
            Item(R.id.shortcut_network, R.id.shortcut_network_label, R.id.shortcut_network_badge, R.string.push_network, NotificationDestination.NETWORK, values.network),
            Item(R.id.shortcut_photos, R.id.shortcut_photos_label, R.id.shortcut_photos_badge, R.string.push_photos, NotificationDestination.PHOTOS, values.photos),
            Item(R.id.shortcut_unused, R.id.shortcut_unused_label, R.id.shortcut_unused_badge, R.string.push_unused, NotificationDestination.UNUSED_FILES, values.unusedFiles),
        )
        for (item in items) {
            val label = context.getString(item.label)
            views.setTextViewText(item.text, label)
            views.setContentDescription(item.root, listOfNotNull(label, item.badge).joinToString(", "))
            views.setOnClickPendingIntent(item.root, contentIntent(item.destination))
            views.setTextViewText(item.badgeView, item.badge.orEmpty())
            views.setViewVisibility(item.badgeView, if (item.badge == null || (compact && largeText)) View.GONE else View.VISIBLE)
        }
        views.setOnClickPendingIntent(R.id.notification_shortcuts, contentIntent(NotificationDestination.HOME))
        return views
    }

    private data class Item(val root: Int, val text: Int, val badgeView: Int, val label: Int,
        val destination: NotificationDestination, val badge: String?)
}
