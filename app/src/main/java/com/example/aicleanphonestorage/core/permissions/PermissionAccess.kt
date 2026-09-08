package com.example.aicleanphonestorage.core.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor

internal enum class PermissionKind {
    USAGE,
    NOTIFICATIONS,
    ALL_FILES,
    PHOTOS,
    PHONE,
    DIRECTORY;

    val special: Boolean
        get() = this == USAGE || this == NOTIFICATIONS || this == ALL_FILES
}

internal interface PermissionAccess {
    suspend fun granted(kind: PermissionKind): Boolean

    suspend fun persistDirectory(uri: String)
}

/** 所有权限都检查真实系统状态；Manifest 声明、设置页 resultCode 都不等于已经授权。 */
internal object PermissionChecks {
    @Suppress("DEPRECATION")
    fun usage(context: Context) =
        context
            .getSystemService(AppOpsManager::class.java)
            ?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED

    fun notifications(context: Context, component: ComponentName) =
        if (Build.VERSION.SDK_INT >= 27)
            context
                .getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component) == true
        else
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

    fun runtimePermissions(kind: PermissionKind): Array<String> =
        when (kind) {
            PermissionKind.PHONE ->
                if (Build.VERSION.SDK_INT <= 28) arrayOf(Manifest.permission.READ_PHONE_STATE)
                else emptyArray()
            PermissionKind.PHOTOS ->
                when {
                    Build.VERSION.SDK_INT >= 34 ->
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                        )
                    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    Build.VERSION.SDK_INT <= 28 ->
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        )
                    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            else -> emptyArray()
        }

    fun grantedRuntime(context: Context, kind: PermissionKind): Boolean {
        fun has(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        if (kind == PermissionKind.PHOTOS) {
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) return true
            if (Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.READ_MEDIA_IMAGES))
                return true
            if (
                Build.VERSION.SDK_INT >= 34 &&
                    has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            )
                return true
        }
        return runtimePermissions(kind).all(::has)
    }
}

internal class AndroidPermissionAccess(
    context: Context,
    val notificationComponent: ComponentName,
    private val executor: TaskExecutor,
) : PermissionAccess {
    private val app = context.applicationContext

    override suspend fun granted(kind: PermissionKind) =
        executor.io {
            when (kind) {
                PermissionKind.USAGE -> PermissionChecks.usage(app)
                PermissionKind.NOTIFICATIONS ->
                    PermissionChecks.notifications(app, notificationComponent)
                PermissionKind.ALL_FILES ->
                    Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
                PermissionKind.PHOTOS,
                PermissionKind.PHONE -> PermissionChecks.grantedRuntime(app, kind)
                PermissionKind.DIRECTORY -> false // 授权范围以系统选择器返回的 URI 为准。
            }
        }

    override suspend fun persistDirectory(uri: String) =
        executor.io {
            app.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
}
