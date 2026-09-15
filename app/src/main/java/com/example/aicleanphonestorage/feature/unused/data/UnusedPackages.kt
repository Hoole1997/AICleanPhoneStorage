package com.example.aicleanphonestorage.feature.unused.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** null 表示未知，不能把可见性限制或查询失败当作卸载。每次删除重新查询，不缓存负结果。 */
internal interface UnusedPackages {
    val completeVisibility: Boolean
    fun installed(packageName: String): Boolean?
}

internal class AndroidUnusedPackages(context: Context) : UnusedPackages {
    private val manager = context.applicationContext.packageManager
    override val completeVisibility get() = Build.VERSION.SDK_INT < 30
    override fun installed(packageName: String): Boolean? = try {
        manager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        if (completeVisibility) false else null
    } catch (_: RuntimeException) { null }
}
