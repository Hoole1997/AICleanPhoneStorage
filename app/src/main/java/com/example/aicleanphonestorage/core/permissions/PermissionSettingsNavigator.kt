package com.example.aicleanphonestorage.core.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 只使用公开路由。本次设置页不留历史；返回只定位本应用申请页，不枚举/结束其他应用任务。 */
internal object PermissionSettingsNavigator {
    const val RETURN_REQUEST = "permission.return.request"

    fun intents(
        kind: PermissionKind,
        packageName: String,
        notificationComponent: ComponentName?,
    ): List<Intent> =
        buildList {
                when (kind) {
                    PermissionKind.USAGE -> {
                        add(
                            Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            )
                        )
                        add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    PermissionKind.NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= 30 && notificationComponent != null)
                            add(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                    .putExtra(
                                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                        notificationComponent.flattenToString(),
                                    )
                            )
                        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    PermissionKind.ALL_FILES -> {
                        if (Build.VERSION.SDK_INT >= 30) {
                            add(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.fromParts("package", packageName, null),
                                )
                            )
                            add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                    else ->
                        add(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            )
                        )
                }
            }
            .onEach {
                it.addFlags(
                    Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }

    fun open(activity: Activity, kind: PermissionKind, component: ComponentName?): Boolean {
        for (intent in intents(kind, activity.packageName, component)) try {
            activity.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        return false
    }

    fun returnIntent(activity: Activity, id: String) =
        returnIntent(ComponentName(activity, activity.javaClass), id)

    fun returnIntent(component: ComponentName, id: String) =
        Intent()
            .setComponent(component)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(RETURN_REQUEST, id)

    fun returnToApp(activity: Activity, id: String): Boolean =
        try {
            activity.startActivity(returnIntent(activity, id))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
