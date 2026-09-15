package com.example.aicleanphonestorage.feature.push

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.docview.push.NotificationRuntime
import kotlinx.coroutines.launch

/** 传入的 Activity 负责触发，XXPermissions 负责系统请求；拒绝结果先存入 ViewModel，恢复前台后才展示引导。 */
internal class PushPermissionCoordinator(
    private val activity: AppCompatActivity,
    private val runtime: NotificationRuntime,
    private val model: PushPermissionViewModel,
    private val otherPermissionPending: () -> Boolean,
    private val openSettings: () -> Unit,
    private val requester: PushPermissionRequester = XxPushPermissionRequester(activity),
    private val allowGuide: Boolean = true,
) {
    init {
        activity.supportFragmentManager.setFragmentResultListener(
            PushPermissionGuideDialog.RESULT,
            activity,
        ) { _, result ->
            model.guideAction(result.getBoolean(PushPermissionGuideDialog.ALLOW))
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.state.collect { drain() } }
        }
    }

    fun onResume() {
        val granted = requester.isGranted()
        model.onForeground(granted, settingsPending = otherPermissionPending(),
            canRequestSystem = !requester.needsSettings(PushPermissionRequest.AUTOMATIC), allowGuide = allowGuide)
        if (granted) runtime.refreshResident()
    }

    fun drain() {
        val manager = activity.supportFragmentManager
        if (
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                activity.isFinishing ||
                activity.isDestroyed ||
                manager.isStateSaved ||
                otherPermissionPending()
        )
            return
        val existing =
            manager.findFragmentByTag(PushPermissionGuideDialog.TAG) as? PushPermissionGuideDialog
        if (requester.isGranted()) {
            model.onForeground(true)
            existing?.dismiss()
            return
        }
        if (model.state.value.guideVisible) {
            if (existing == null)
                PushPermissionGuideDialog().showNow(manager, PushPermissionGuideDialog.TAG)
            model.guideShown()
            return
        }
        // FragmentResult 可在关闭动画尚未结束时到达，先移除旧引导，再启动系统授权界面。
        existing?.dismissNow()
        val origin = model.takeRequest() ?: return
        if (requester.needsSettings(origin)) {
            // 此分支只来自引导框的 Allow。共享权限流程负责有限期检测与返回，避免两套后台监测。
            openSettings()
        } else {
            requester.request(origin) { granted, denied -> complete(granted, denied) }
        }
    }

    fun onSettingsResult(granted: Boolean) = complete(granted, denied = false)

    private fun complete(granted: Boolean, denied: Boolean) {
        model.onResult(granted, denied)
        if (granted) runtime.refreshResident()
    }

    companion object {
        const val SETTINGS_ROUTE = "permission.push.settings"

        /** permissions 必须是当前 activity 的共享协调器；系统回跳由它定位传入的宿主 Activity。 */
        fun attach(
            activity: AppCompatActivity,
            runtime: NotificationRuntime,
            model: PushPermissionViewModel,
            permissions: com.example.aicleanphonestorage.core.permissions.PermissionCoordinator,
            beforeSettings: () -> Unit = {},
            allowGuide: Boolean = true,
        ): PushPermissionCoordinator {
            val coordinator =
                PushPermissionCoordinator(
                    activity,
                    runtime,
                    model,
                    { permissions.pending },
                    {
                        permissions.request(
                            SETTINGS_ROUTE,
                            com.example.aicleanphonestorage.core.permissions.PermissionKind
                                .POST_NOTIFICATIONS,
                            settings = true,
                        )
                    },
                    allowGuide = allowGuide,
                )
            permissions.register(
                SETTINGS_ROUTE,
                before = { beforeSettings() },
                result = { coordinator.onSettingsResult(it.granted) },
            )
            return coordinator
        }
    }
}
