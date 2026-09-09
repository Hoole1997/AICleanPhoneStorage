package com.example.aicleanphonestorage.feature.push

import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.XXPermissions
import io.docview.push.NotificationRuntime
import io.docview.push.NotificationPermissionAccess

/** Activity 级协调器，只由首页 onResume 调用；不保留 Activity 到应用级对象，不进行后台轮询。 */
internal class PushPermissionCoordinator(
    private val activity: AppCompatActivity,
    private val runtime: NotificationRuntime,
) {
    private var requesting = false

    fun onResume(otherPermissionPending: Boolean) {
        if (NotificationPermissionAccess.isGranted(activity)) {
            runtime.refreshResident()
            return
        }
        // 系统授权界面返回时也会触发 onResume；等原请求回调结束，避免嵌套申请。
        if (requesting || otherPermissionPending || activity.isFinishing || activity.isDestroyed ||
            activity.supportFragmentManager.isStateSaved) return
        requesting = true
        try {
            XXPermissions.with(activity)
                .permission(NotificationPermissionAccess.permissionToRequest(activity))
                .request { _, deniedList ->
                    requesting = false
                    if (deniedList.isEmpty() && NotificationPermissionAccess.isGranted(activity)) {
                        runtime.refreshResident()
                    }
                    // 拒绝后不在回调里重试；下次首页 onResume 再按真实状态判断。
                }
        } catch (error: RuntimeException) {
            requesting = false
            throw error
        }
    }
}
