package com.example.aicleanphonestorage.feature.appmanager.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 直接进入所点应用的详情页；不经过应用列表，不自动点击卸载、清数据或权限开关。 */
internal object AppDetailsSettings {
    fun intent(packageName: String) =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )

    fun open(activity: Activity, packageName: String): Boolean =
        try {
            // 不用 resolveActivity 做前置拦截：包可见性限制下返回 null 也可能正常启动系统设置。
            activity.startActivity(intent(packageName))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
