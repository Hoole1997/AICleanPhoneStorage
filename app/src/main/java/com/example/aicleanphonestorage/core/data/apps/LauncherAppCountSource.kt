package com.example.aicleanphonestorage.core.data.apps

import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 沿用首页原有口径：当前用户可见的 LAUNCHER 应用按包名去重，不读取标签、图标或存储明细。 */
internal class LauncherAppCountSource(context: Context, private val executor: TaskExecutor) {
    private val app = context.applicationContext

    @Suppress("DEPRECATION")
    suspend fun read(): Int = executor.io {
        val activities = app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val packages = HashSet<String>()
        for (activity in activities) {
            currentCoroutineContext().ensureActive()
            packages += activity.activityInfo.packageName
        }
        packages.size
    }
}
