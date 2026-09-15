package com.example.aicleanphonestorage.feature.push

import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.XXPermissions
import io.docview.push.NotificationPermissionAccess

internal interface PushPermissionRequester {
    fun isGranted(): Boolean

    fun needsSettings(origin: PushPermissionRequest): Boolean

    fun request(origin: PushPermissionRequest, result: (granted: Boolean, denied: Boolean) -> Unit)
}

/** 唯一系统权限边界；普通拒绝可重试，永久拒绝仅在用户点 Allow 后进入通知设置。 */
internal class XxPushPermissionRequester(private val activity: AppCompatActivity) :
    PushPermissionRequester {
    override fun isGranted() = NotificationPermissionAccess.isGranted(activity)

    override fun needsSettings(origin: PushPermissionRequest): Boolean {
        val permission = NotificationPermissionAccess.permissionToRequest(activity)
        // 旧系统或通知总开关被关闭时都只能进入设置；不是只有“不再询问”才需要自动返回。
        return android.os.Build.VERSION.SDK_INT < 33 ||
            XXPermissions.isGrantedPermission(
                activity,
                com.hjq.permissions.permission.PermissionLists.getPostNotificationsPermission(),
            ) ||
            XXPermissions.isDoNotAskAgainPermission(activity, permission)
    }

    override fun request(origin: PushPermissionRequest, result: (Boolean, Boolean) -> Unit) {
        val permission = NotificationPermissionAccess.permissionToRequest(activity)
        try {
            XXPermissions.with(activity).permission(permission).request { _, denied ->
                result(isGranted(), denied.isNotEmpty())
            }
        } catch (_: RuntimeException) {
            // 无法发起请求不等于系统拒绝，不用于触发引导。
            result(isGranted(), false)
        }
    }
}
