package com.example.aicleanphonestorage.core.permissions

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists

/** 复用公共权限状态机和弹框；视频的系统申请/永久拒绝判断统一由现有 XXPermissions 负责。 */
internal object VideoPermissionRequest {
    fun permissions() = when {
        Build.VERSION.SDK_INT >= 34 -> listOf(PermissionLists.getReadMediaVideoPermission(), PermissionLists.getReadMediaVisualUserSelectedPermission())
        Build.VERSION.SDK_INT >= 33 -> listOf(PermissionLists.getReadMediaVideoPermission())
        Build.VERSION.SDK_INT <= 28 -> listOf(PermissionLists.getReadExternalStoragePermission(), PermissionLists.getWriteExternalStoragePermission())
        else -> listOf(PermissionLists.getReadExternalStoragePermission())
    }

    fun needsSettings(activity: AppCompatActivity) = permissions().any {
        !XXPermissions.isGrantedPermission(activity, it) && XXPermissions.isDoNotAskAgainPermission(activity, it)
    }

    fun request(activity: AppCompatActivity, result: () -> Unit) {
        try {
            XXPermissions.with(activity).permissions(permissions()).request { _, _ -> result() }
        } catch (_: RuntimeException) {
            // OEM 请求入口不可用时仍结束等待；授权事实由公共状态机重新读取。
            result()
        }
    }
}
