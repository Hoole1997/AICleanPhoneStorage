package com.example.aicleanphonestorage.feature.networktraffic.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** Manifest 声明不代表已授权；系统设置返回时检查真实 AppOp，不依赖 resultCode。 */
class UsageAccessChecker(context: Context) {
    private val app = context.applicationContext
    @Suppress("DEPRECATION")
    fun isGranted(): Boolean = app.getSystemService(AppOpsManager::class.java)
        ?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName) == AppOpsManager.MODE_ALLOWED
}
