package com.example.aicleanphonestorage.feature.networktraffic.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** 只按系统 UID/安装标志过滤，不用应用名称或包名前缀猜测，避免误伤普通应用。 */
internal object TrafficAppVisibility {
    fun isApplicationUid(uid: Int): Boolean = uid >= 0 && uid % 100_000 in 10_000..19_999
    fun isUserInstalled(flags: Int): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
}

/** 数据层只解析轻量文字和包标识，图标留给可见列表项按需读取。 */
internal class AppMetadataDataSource(context: Context) {
    private val manager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    fun applicationsForUid(uid: Int): List<AppIdentity> {
        if (!TrafficAppVisibility.isApplicationUid(uid)) return emptyList()
        val packages = manager.getPackagesForUid(uid).orEmpty()
        val applications = ArrayList<ApplicationInfo>(packages.size)
        for (packageName in packages) {
            val info = try {
                manager.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return emptyList() // 已卸载或不可见的 UID 不生成无法管理的占位行。
            }
            // 流量只能按 UID 归属；共享 UID 含系统包时整组排除，不能把系统流量归到普通包。
            if (!TrafficAppVisibility.isUserInstalled(info.flags)) return emptyList()
            applications += info
        }
        return applications.map { AppIdentity(it.packageName, manager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label }
    }
}
