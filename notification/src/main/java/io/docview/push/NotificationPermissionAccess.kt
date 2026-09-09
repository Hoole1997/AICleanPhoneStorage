package io.docview.push

import android.content.Context
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

/** 请求入口与发布前检查共用 XXPermissions；通知监听权限是另一个独立功能，不在此申请。 */
object NotificationPermissionAccess {
    fun isGranted(context: Context, channelId: String? = null): Boolean {
        val post = PermissionLists.getPostNotificationsPermission()
        val notifications = if (channelId == null) PermissionLists.getNotificationServicePermission()
            else PermissionLists.getNotificationServicePermission(channelId)
        return XXPermissions.isGrantedPermission(context, post) &&
            XXPermissions.isGrantedPermission(context, notifications)
    }

    fun permissionToRequest(context: Context): IPermission {
        val post = PermissionLists.getPostNotificationsPermission()
        // 运行时权限已授予但 OEM 通知总开关关闭时，按官方示例申请通知服务设置权限。
        return if (XXPermissions.isGrantedPermission(context, post))
            PermissionLists.getNotificationServicePermission() else post
    }
}
