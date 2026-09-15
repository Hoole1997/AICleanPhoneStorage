package com.example.aicleanphonestorage.feature.push

import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.XXPermissions
import io.docview.push.NotificationPermissionAccess

internal interface PushPermissionRequester {
    fun isGranted(): Boolean
    fun needsSettings(origin: PushPermissionRequest): Boolean

    /** onStarted 只能在确认未授权、马上调用真正的申请 API 时执行。 */
    fun request(origin: PushPermissionRequest, onStarted: () -> Unit, result: (PushPermissionOutcome) -> Unit)
}

/** 唯一系统权限边界；普通拒绝可重试，永久拒绝仅在用户点 Allow 后进入通知设置。 */
internal class XxPushPermissionRequester(private val activity: AppCompatActivity) : PushPermissionRequester {
    override fun isGranted() = NotificationPermissionAccess.isGranted(activity)

    override fun needsSettings(origin: PushPermissionRequest): Boolean {
        val permission = NotificationPermissionAccess.permissionToRequest(activity)
        return android.os.Build.VERSION.SDK_INT < 33 ||
            XXPermissions.isGrantedPermission(activity,
                com.hjq.permissions.permission.PermissionLists.getPostNotificationsPermission()) ||
            XXPermissions.isDoNotAskAgainPermission(activity, permission)
    }

    override fun request(origin: PushPermissionRequest, onStarted: () -> Unit, result: (PushPermissionOutcome) -> Unit) {
        // 排队期间或另一个授权入口可能已获批，不能虚报 Start；埋点层按系统版本决定是否报 allow1。
        if (isGranted()) { result(PushPermissionOutcome.ALREADY_ALLOWED); return }
        try {
            val permission = NotificationPermissionAccess.permissionToRequest(activity)
            val request = XXPermissions.with(activity).permission(permission)
            if (isGranted()) { result(PushPermissionOutcome.ALREADY_ALLOWED); return }
            onStarted()
            request.request { _, denied ->
                result(when {
                    isGranted() -> PushPermissionOutcome.ALLOWED
                    denied.isEmpty() -> PushPermissionOutcome.UNAVAILABLE
                    // 只在真实拒绝回调后查询“不再询问”，不把任意未授权/异常推断为永久拒绝。
                    denied.any { XXPermissions.isDoNotAskAgainPermission(activity, it) } -> PushPermissionOutcome.DENIED_FOREVER
                    else -> PushPermissionOutcome.DENIED
                })
            }
        } catch (_: RuntimeException) {
            // 无法完成请求没有协议中的授权结果，仍结束 UI 流程，但不伪造 denied/forever。
            result(PushPermissionOutcome.UNAVAILABLE)
        }
    }
}
