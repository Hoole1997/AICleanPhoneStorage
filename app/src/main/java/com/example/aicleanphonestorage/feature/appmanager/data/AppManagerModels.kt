package com.example.aicleanphonestorage.feature.appmanager.data

import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import java.text.Collator
import java.util.Locale

internal enum class AppSortKey {
    LAST_USED,
    SIZE,
    NAME,
}

internal data class AppManagerSort(
    val key: AppSortKey = AppSortKey.LAST_USED,
    val descending: Boolean = true,
) {
    fun select(next: AppSortKey) =
        if (next == key) copy(descending = !descending)
        else AppManagerSort(next, descending = next != AppSortKey.NAME)
}

internal enum class AppSizeKind {
    USED,
    SHARED_UID,
    APK,
}

internal sealed interface AppLastUse {
    data class Recorded(val timeMillis: Long) : AppLastUse

    data object NoRecentRecord : AppLastUse

    data object Unavailable : AppLastUse
}

internal data class ManagedApp(
    val identity: InstalledAppSummary,
    val installedAt: Long? = null,
    val sizeBytes: Long? = null,
    val sizeKind: AppSizeKind = AppSizeKind.APK,
    val lastUse: AppLastUse = AppLastUse.Unavailable,
    val canUninstall: Boolean = false,
) {
    val packageName
        get() = identity.packageName

    val label
        get() = identity.label
}

internal object AppManagerOrdering {
    fun sorted(apps: List<ManagedApp>, sort: AppManagerSort, locale: Locale): List<ManagedApp> {
        val names = Collator.getInstance(locale)
        fun number(a: Long?, b: Long?): Int =
            when {
                a == null && b == null -> 0
                a == null -> 1
                b == null -> -1
                sort.descending -> b.compareTo(a)
                else -> a.compareTo(b)
            }
        fun lastUse(app: ManagedApp): Long? =
            when (val usage = app.lastUse) {
                is AppLastUse.Recorded -> usage.timeMillis
                AppLastUse.NoRecentRecord -> 0L // 仅用于排序，不当作“从未使用”的事实显示。
                AppLastUse.Unavailable -> null
            }
        return apps.sortedWith { a, b ->
            val primary =
                when (sort.key) {
                    AppSortKey.LAST_USED -> number(lastUse(a), lastUse(b))
                    AppSortKey.SIZE -> number(a.sizeBytes, b.sizeBytes)
                    AppSortKey.NAME ->
                        if (sort.descending) names.compare(b.label, a.label)
                        else names.compare(a.label, b.label)
                }
            primary.takeIf { it != 0 }
                ?: names.compare(a.label, b.label).takeIf { it != 0 }
                ?: a.packageName.compareTo(b.packageName)
        }
    }
}
