package com.example.aicleanphonestorage.core.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import java.text.Collator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class InstalledAppSummary(
    val packageName: String,
    val label: String,
    val installed: Boolean = true,
)

/** 共享应用元数据读取器：只查询桌面入口，不读 APK 内容，不加载 Bitmap，不要求全量包权限。 */
internal class InstalledAppsReader(context: Context, private val executor: TaskExecutor) {
    private val app = context.applicationContext

    @Suppress("DEPRECATION")
    suspend fun read(
        additionalPackages: Set<String> = emptySet(),
        excludedPackages: Set<String> = emptySet(),
        retainMissing: Boolean = false,
        progress: (Int, Int) -> Unit,
    ): List<InstalledAppSummary> =
        executor.io {
            val manager = app.packageManager
            val names =
                (manager
                        .queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            0,
                        )
                        .map { it.activityInfo.packageName } + additionalPackages)
                    .distinct()
                    .filterNot(excludedPackages::contains)
            val rows = ArrayList<InstalledAppSummary>(names.size)
            progress(0, names.size)
            for ((index, name) in names.withIndex()) {
                currentCoroutineContext().ensureActive()
                try {
                    val info = manager.getApplicationInfo(name, 0)
                    rows +=
                        InstalledAppSummary(
                            name,
                            manager.getApplicationLabel(info).toString().ifBlank { name },
                        )
                } catch (_: PackageManager.NameNotFoundException) {
                    // 通知规则需要保留已卸载应用以便取消规则；管理列表则跳过刚被卸载的条目。
                    if (retainMissing) rows += InstalledAppSummary(name, name, false)
                }
                progress(index + 1, names.size)
            }
            currentCoroutineContext().ensureActive()
            val collator = Collator.getInstance(app.resources.configuration.locales[0])
            rows.sortedWith { a, b ->
                collator.compare(a.label, b.label).takeIf { it != 0 }
                    ?: a.packageName.compareTo(b.packageName)
            }
        }
}
