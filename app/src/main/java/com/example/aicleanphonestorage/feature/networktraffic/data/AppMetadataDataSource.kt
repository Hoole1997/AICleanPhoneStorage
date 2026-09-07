package com.example.aicleanphonestorage.feature.networktraffic.data

import android.content.Context
import android.content.pm.PackageManager

/** 数据层只解析轻量文字和包标识，图标留给可见列表项按需读取。 */
internal class AppMetadataDataSource(context: Context) {
    private val manager = context.applicationContext.packageManager
    @Suppress("DEPRECATION")
    fun applicationsForUid(uid: Int): List<AppIdentity> = manager.getPackagesForUid(uid).orEmpty().mapNotNull { packageName ->
        try {
            val info = manager.getApplicationInfo(packageName, 0)
            AppIdentity(packageName, manager.getApplicationLabel(info).toString())
        } catch (error: PackageManager.NameNotFoundException) {
            null // 查询期间卸载或包可见性过滤，UID 汇总仍然保留，不猜测名称。
        }
    }.sortedBy { it.label }
}
