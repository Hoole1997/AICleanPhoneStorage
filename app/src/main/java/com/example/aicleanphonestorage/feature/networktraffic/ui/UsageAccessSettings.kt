package com.example.aicleanphonestorage.feature.networktraffic.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 使用公开 Intent + package URI 直达本应用。OEM 不支持详情路由时才回退到授权列表。 */
internal object UsageAccessSettings {
    fun detailsIntent(packageName: String) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
    fun open(activity: Activity): Boolean {
        for (intent in listOf(detailsIntent(activity.packageName), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            try { activity.startActivity(intent); return true }
            catch (_: ActivityNotFoundException) { /* 尝试下一个公开路由，不硬编码厂商组件。 */ }
            catch (_: SecurityException) { /* 某些 OEM 路由受限制，回退标准列表。 */ }
        }
        return false
    }
}
