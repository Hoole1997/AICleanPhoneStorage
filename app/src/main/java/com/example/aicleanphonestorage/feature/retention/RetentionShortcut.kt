package com.example.aicleanphonestorage.feature.retention

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.startup.StartupActivity

/** 只管理自家动态快捷入口，不替换系统卸载按钮，不发布固定快捷方式或修改其他业务的快捷入口。 */
internal class RetentionShortcut(context: Context, private val id: String = ID) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(ShortcutManager::class.java)

    /** 仅向系统 Launcher 标记当前快捷入口被使用，不包含反馈或用户输入。 */
    fun recordUse() { manager.reportShortcutUsed(id) }

    fun sync(enabled: Boolean, localized: Context) {
        val dynamic = manager.dynamicShortcuts.firstOrNull { it.id == id }
        val pinned = manager.pinnedShortcuts.firstOrNull { it.id == id }
        if (!enabled) {
            // remove 不会失效用户已固定的副本，必须同时 disable，旧 Intent 仍在启动路由处复核。
            if (dynamic != null || pinned?.isEnabled == true) {
                manager.disableShortcuts(listOf(id), localized.getString(R.string.retention_shortcut_disabled))
                manager.removeDynamicShortcuts(listOf(id))
            }
            return
        }
        val label = localized.getString(R.string.retention_uninstall)
        if (dynamic?.isEnabled == true && dynamic.shortLabel?.toString() == label) return
        if (manager.isRateLimitingActive) return // 由下次前台/配置/归因/语言事件重试，没有轮询或频控规避。
        manager.enableShortcuts(listOf(id))
        manager.addDynamicShortcuts(listOf(ShortcutInfo.Builder(app, id)
            .setActivity(ComponentName(app, StartupActivity::class.java))
            .setShortLabel(label).setLongLabel(label).setRank(0)
            .setIcon(Icon.createWithResource(app, R.drawable.retention_shortcut))
            .setIntent(intent(app)).build()))
    }

    companion object {
        const val ID = "uninstall_retention"
        const val ACTION = "com.example.aicleanphonestorage.action.UNINSTALL_RETENTION"
        fun intent(context: Context) = Intent(context, StartupActivity::class.java)
            .setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
