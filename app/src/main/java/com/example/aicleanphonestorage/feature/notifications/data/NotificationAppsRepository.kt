package com.example.aicleanphonestorage.feature.notifications.data

import android.content.Context
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class NotificationApp(
    val packageName: String,
    val label: String,
    val installed: Boolean = true,
)

data class NotificationCatalog(
    val apps: List<NotificationApp>,
    val initialSelected: Set<String> = emptySet(),
)

interface NotificationAppsRepository {
    val selectedPackages: Flow<Set<String>>

    suspend fun hasAccess(): Boolean

    suspend fun loadApps(progress: (Int, Int) -> Unit): NotificationCatalog

    suspend fun setEnabled(packageName: String, enabled: Boolean)

    suspend fun setSelection(packages: Set<String>) {
        val previous = selectedPackages.first()
        for (name in previous - packages) setEnabled(name, false)
        for (name in packages - previous) setEnabled(name, true)
    }
}

class AndroidNotificationAppsRepository(
    context: Context,
    private val rules: NotificationRulesStore,
    private val executor: TaskExecutor,
) : NotificationAppsRepository {
    private val app = context.applicationContext
    private val access = NotificationAccess(app)
    private val reader =
        com.example.aicleanphonestorage.core.data.apps.InstalledAppsReader(app, executor)
    override val selectedPackages = rules.selectedPackages

    override suspend fun hasAccess(): Boolean = executor.io { access.isGranted() }

    @Suppress("DEPRECATION")
    override suspend fun loadApps(progress: (Int, Int) -> Unit): NotificationCatalog {
        if (!hasAccess()) throw SecurityException("Notification access required")
        val storedSelection = rules.selectedPackages.first()
        val rows =
            reader.read(
                storedSelection,
                setOf(app.packageName),
                retainMissing = true,
                progress = progress,
            )
        return NotificationCatalog(
            rows.map { NotificationApp(it.packageName, it.label, it.installed) },
            storedSelection,
        )
    }

    override suspend fun setSelection(packages: Set<String>) {
        require(app.packageName !in packages)
        if (!hasAccess()) throw SecurityException("Notification access required")
        rules.setSelection(packages)
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        require(packageName != app.packageName) // 保护清理应用自身的必要状态通知。
        rules.setEnabled(packageName, enabled)
    }
}
