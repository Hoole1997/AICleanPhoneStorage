package com.example.aicleanphonestorage.feature.notifications.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.Collator

data class NotificationApp(val packageName: String, val label: String, val installed: Boolean = true)
data class NotificationCatalog(val apps: List<NotificationApp>, val initialSelected: Set<String> = emptySet())

interface NotificationAppsRepository {
    val selectedPackages: Flow<Set<String>>
    suspend fun hasAccess(): Boolean
    suspend fun loadApps(progress: (Int, Int) -> Unit): NotificationCatalog
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}

class AndroidNotificationAppsRepository(
    context: Context,
    private val rules: NotificationRulesStore,
    private val executor: TaskExecutor,
) : NotificationAppsRepository {
    private val app = context.applicationContext
    private val access = NotificationAccess(app)
    override val selectedPackages = rules.selectedPackages
    override suspend fun hasAccess(): Boolean = executor.io { access.isGranted() }

    @Suppress("DEPRECATION")
    override suspend fun loadApps(progress: (Int, Int) -> Unit): NotificationCatalog = executor.io {
        if (!access.isGranted()) throw SecurityException("Notification access required")
        val manager = app.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // 复用有限queries声明；已选择但暂不可见/已卸载的包仍列出，允许用户取消规则。
        val storedSelection = rules.selectedPackages.first()
        val packages = (manager.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName } + storedSelection)
            .filter { it != app.packageName }.distinct()
        val result = ArrayList<NotificationApp>(packages.size)
        for ((index, name) in packages.withIndex()) {
            currentCoroutineContext().ensureActive()
            val row = try {
                val info = manager.getApplicationInfo(name, 0)
                NotificationApp(name, manager.getApplicationLabel(info).toString())
            } catch (_: PackageManager.NameNotFoundException) { NotificationApp(name, name, installed = false) }
            result += row
            progress(index + 1, packages.size)
        }
        val collator = Collator.getInstance(app.resources.configuration.locales[0])
        NotificationCatalog(result.sortedWith { a, b -> collator.compare(a.label, b.label) }, storedSelection)
    }
    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        require(packageName != app.packageName) // 保护清理应用自身的必要状态通知。
        rules.setEnabled(packageName, enabled)
    }
}
