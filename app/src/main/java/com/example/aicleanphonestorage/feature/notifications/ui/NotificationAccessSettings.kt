package com.example.aicleanphonestorage.feature.notifications.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.aicleanphonestorage.feature.notifications.data.NotificationAccess

internal object NotificationAccessSettings {
    fun open(activity: Activity): Boolean {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 30) add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationAccess(activity).component.flattenToString()))
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        for (intent in intents) {
            try { activity.startActivity(intent); return true }
            catch (_: ActivityNotFoundException) { /* 详情不支持时回退公开列表入口。 */ }
            catch (_: SecurityException) { /* 保留厂商限制，不尝试绕过授权。 */ }
        }
        return false
    }
}
