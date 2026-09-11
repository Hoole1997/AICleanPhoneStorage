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
            "screenshots" -> CleanupFeature.SCREENSHOTS
            "photo" -> CleanupFeature.PHOTO_COMPRESS
            "large" -> CleanupFeature.LARGE_FILES
            "unused", "unused_detail" -> CleanupFeature.UNUSED_FILES
            else -> null
        }
        try {
            when {
                feature != null -> (app.container.fileScanRepository.resolveAccess(feature).request == AccessRequest.NONE).toString()
                page == "traffic" -> app.container.permissionAccess.granted(PermissionKind.USAGE).toString()
                page == "notify" -> app.container.permissionAccess.granted(PermissionKind.NOTIFICATIONS).toString()
                else -> "none"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { "false" }
    }
    fun entry(app: CleanApplication, entry: String) = BusinessTelemetry.withPermission(
        MetricEvent.FEATURE_ENTRY_CLICK, mapOf("entry" to entry), permission(app, entry))

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
