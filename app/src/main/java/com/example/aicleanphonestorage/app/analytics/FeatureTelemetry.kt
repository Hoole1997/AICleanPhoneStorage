package com.example.aicleanphonestorage.app.analytics

import com.example.aicleanphonestorage.core.analytics.*

import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.permissions.PermissionKind
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.scan.AccessRequest
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot
import kotlinx.coroutines.CancellationException

/** 权限只查询已有业务授权解析器，不为埋点申请权限或读取文件/应用明细。 */
internal object FeatureTelemetry {
    fun permission(app: CleanApplication, page: String): suspend () -> String = {
        val feature = when (page) {
            "junk", "junk_detail" -> CleanupFeature.SMART_CLEAN
            "duplicate" -> CleanupFeature.SIMILAR_PHOTOS
            "video" -> CleanupFeature.VIDEOS
            "screenshots" -> CleanupFeature.SCREENSHOTS
            "photo" -> CleanupFeature.PHOTO_COMPRESS
            "large" -> CleanupFeature.LARGE_FILES
            "unused", "unused_detail" -> CleanupFeature.UNUSED_FILES
            else -> null
        }
        try {
            when {
                feature != null -> (app.container.fileScanRepository.resolveAccess(feature).request == AccessRequest.NONE).toString()
                page == "malware" -> app.container.permissionAccess.granted(
                    if (android.os.Build.VERSION.SDK_INT >= 30) PermissionKind.ALL_FILES else PermissionKind.READ_FILES).toString()
                page == "traffic" -> app.container.permissionAccess.granted(PermissionKind.USAGE).toString()
                page == "notify" -> app.container.permissionAccess.granted(PermissionKind.NOTIFICATIONS).toString()
                else -> "none"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { "false" }
    }
    fun entry(app: CleanApplication, entry: String, homeBadgeVisible: Boolean) = BusinessTelemetry.withPermission(
        MetricEvent.FEATURE_ENTRY_CLICK, entryParameters(entry, homeBadgeVisible), permission(app, entry))

    /** 首页角标在 UI 点击时采集；这里只转换飞书 sheet01 E54:I55 的 yes/no，不能等异步权限采集后读取 UI。 */
    fun entryParameters(entry: String, homeBadgeVisible: Boolean): Map<String, Any> = mapOf(
        // 测速入口协议是 speedtest，测速页面协议是 speedtestScan；二者不能共用页面名称转换。
        "entry" to if (entry == "speedtest") "speedtest" else BusinessPageNames.wire(entry),
        "home_badge" to if (homeBadgeVisible) "yes" else "no",
    )

    fun homeState(snapshot: HomeCleaningSnapshot): Map<String, Any> = buildMap {
        put("state", if (snapshot.dirty) "dirty" else "cleaned")
        if (snapshot.dirty) put("junk_size", requireNotNull(snapshot.junkTenthsMb) / 10.0)
    }

    fun sizeBand(bytes: Long?): String? = when {
        bytes == null || bytes < 0 -> null
        bytes < 50_000_000 -> "lt50mb"
        bytes <= 200_000_000 -> "50_200mb"
        bytes <= 500_000_000 -> "200_500mb"
        else -> "gt500mb"
    }
}
