package com.example.aicleanphonestorage.feature.appmanager.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.os.storage.StorageManager
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.core.data.apps.InstalledAppsReader
import com.example.aicleanphonestorage.core.permissions.PermissionChecks
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AppManagerCatalog(val apps: List<ManagedApp>, val usageAccess: Boolean = false)

internal interface AppManagerRepository {
    suspend fun load(progress: (Int, Int) -> Unit = { _, _ -> }): AppManagerCatalog
}

/** 两阶段读取避免嵌套 I/O 许可；历史一次批量查询，存储按 UID 去重，所有 Binder/文件元数据读取均离开主线程。 */
internal class AndroidAppManagerRepository(
    context: Context,
    private val reader: InstalledAppsReader,
    private val executor: TaskExecutor,
) : AppManagerRepository {
    private val app = context.applicationContext
    private val lock = Mutex()

    @Suppress("DEPRECATION")
    override suspend fun load(progress: (Int, Int) -> Unit): AppManagerCatalog =
        lock.withLock {
            val identities = reader.read(progress = { _, _ -> })
            executor.io {
                val manager = app.packageManager
                val granted = PermissionChecks.usage(app)
                val now = System.currentTimeMillis()
                val start = (now - 90L * 86_400_000).coerceAtLeast(0)
                val usage =
                    if (
                        granted &&
                            app.getSystemService(UserManager::class.java)?.isUserUnlocked == true
                    )
                        optional {
                            app.getSystemService(UsageStatsManager::class.java)
                                ?.queryAndAggregateUsageStats(start, now)
                        }
                    else null
                val storage = app.getSystemService(StorageStatsManager::class.java)
                val sizes = mutableMapOf<Pair<java.util.UUID, Int>, Long?>()
                val rows = ArrayList<ManagedApp>(identities.size)
                progress(0, identities.size)
                for ((index, identity) in identities.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    try {
                        val pkg = manager.getPackageInfo(identity.packageName, 0)
                        val info =
                            pkg.applicationInfo
                                ?: manager.getApplicationInfo(identity.packageName, 0)
                        val installed = pkg.firstInstallTime.takeIf { it > 0 }
                        val used = usage?.get(identity.packageName)?.lastTimeUsed
                        val lastUse =
                            when {
                                usage == null -> AppLastUse.Unavailable
                                used != null &&
                                    used >= maxOf(start, installed ?: 0) &&
                                    used <= now -> AppLastUse.Recorded(used)
                                else -> AppLastUse.NoRecentRecord
                            }
                        // 未授权时统一使用 APK 大小；授权后的查询失败显示未知，不混排两种不同口径。
                        val shared =
                            !pkg.sharedUserId.isNullOrBlank() || info.uid % 100_000 < 10_000
                        val kind =
                            if (!granted) AppSizeKind.APK
                            else if (shared) AppSizeKind.SHARED_UID else AppSizeKind.USED
                        val size =
                            if (!granted) apkSize(info)
                            else {
                                val key =
                                    (info.storageUuid ?: StorageManager.UUID_DEFAULT) to info.uid
                                if (key !in sizes)
                                    sizes[key] = optional {
                                        storage?.queryStatsForUid(key.first, key.second)?.let {
                                            stats ->
                                            if (stats.appBytes < 0 || stats.dataBytes < 0) null
                                            else sum(stats.appBytes, stats.dataBytes)
                                        }
                                    }
                                sizes[key]
                            }
                        rows +=
                            ManagedApp(
                                identity,
                                installed,
                                size,
                                kind,
                                lastUse,
                                canUninstall =
                                    identity.packageName != app.packageName &&
                                        info.flags and
                                            (ApplicationInfo.FLAG_SYSTEM or
                                                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                            )
                    } catch (_: PackageManager.NameNotFoundException) {
                        // 卸载/替换期间再次验证，已经消失的应用不保留可操作卡片。
                    }
                    progress(index + 1, identities.size)
                }
                currentCoroutineContext().ensureActive()
                AppManagerCatalog(rows, granted)
            }
        }

    private fun apkSize(info: ApplicationInfo): Long? = optional {
        val paths =
            (listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty())
                .filter(String::isNotBlank)
                .distinct()
        if (paths.isEmpty()) return@optional null
        var total = 0L
        for (path in paths) {
            val bytes = File(path).length()
            if (bytes <= 0) return@optional null
            total = sum(total, bytes)
        }
        total
    }

    private inline fun <T> optional(read: () -> T): T? =
        try {
            read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun sum(a: Long, b: Long) = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}
