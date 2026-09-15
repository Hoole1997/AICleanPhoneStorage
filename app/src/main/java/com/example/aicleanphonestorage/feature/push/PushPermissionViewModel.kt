package com.example.aicleanphonestorage.feature.push

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PushPermissionRequest {
    AUTOMATIC,
    GUIDE,
}

internal data class PushPermissionState(
    val request: PushPermissionRequest? = null,
    val requesting: Boolean = false,
    val guideVisible: Boolean = false,
    val completed: Boolean = false,
    val ratingAllowed: Boolean = false,
)

/** 只记录本次宿主 Activity 生命周期的流程，不持有 Activity。未授权本身不是展示引导的依据。 */
internal class PushPermissionViewModel(private val saved: SavedStateHandle) : ViewModel() {
    val telemetry = PushPermissionTelemetry(saved, android.os.Build.VERSION.SDK_INT)
    val hasStarted: Boolean get() = saved.get<Boolean>("push.auto.attempted") == true
    private val current =
        MutableStateFlow(
            PushPermissionState(
                request = PushPermissionRequest.entries.firstOrNull { it.name == saved.get<String>("push.request") },
                guideVisible = saved["push.guide.visible"] ?: false,
                completed = saved["push.completed"] ?: false,
                ratingAllowed = saved["push.rating.allowed"] ?: false,
            )
        )
    val state = current.asStateFlow()

    fun onForeground(granted: Boolean, settingsPending: Boolean = false, canRequestSystem: Boolean = true, allowGuide: Boolean = true) {
        if (granted) {
            saved["push.auto.attempted"] = true
            update(PushPermissionState(completed = true, ratingAllowed = true))
        } else if (saved.get<Boolean>("push.auto.attempted") != true && !current.value.requesting) {
            saved["push.auto.attempted"] = true
            if (canRequestSystem) update(current.value.copy(request = PushPermissionRequest.AUTOMATIC))
            else update(PushPermissionState(guideVisible = allowGuide, completed = !allowGuide))
        } else if (
            !settingsPending &&
                !current.value.requesting &&
                current.value.request == null &&
                !current.value.guideVisible &&
                !current.value.completed
        ) {
            // 进程重建后 SDK 的运行时回调无法恢复；没有共享设置请求可续接时正常放行，不伪造拒绝。
            update(PushPermissionState(completed = true))
        } else if (!settingsPending && current.value.completed && !current.value.requesting && !current.value.guideVisible) {
            // 拒绝路径在下一次真正回到首页后才允许好评弹窗。
            update(current.value.copy(ratingAllowed = true))
        }
    }

    fun takeRequest(): PushPermissionRequest? {
        val request = current.value.request ?: return null
        update(current.value.copy(request = null, requesting = true))
        return request
    }

    @Suppress("UNUSED_PARAMETER")
    fun onResult(granted: Boolean, denied: Boolean) {
        // 系统拒绝后直接放行启动链路，不在启动页追加自实现弹层。
        update(PushPermissionState(completed = true, ratingAllowed = granted))
    }

    fun guideShown() {
        saved["push.guide.offered"] = true
    }

    fun guideAction(allow: Boolean) {
        if (allow) telemetry.reset()
        // 确认/关闭都消耗本次引导，避免系统设置返回或旋转后形成弹框循环。
        saved["push.guide.offered"] = true
        update(
            PushPermissionState(
                request = if (allow) PushPermissionRequest.GUIDE else null,
                completed = !allow,
            )
        )
    }

    /** 冷启动从启动页抵达首页时重新检查：首次拒绝后可进行第二次系统询问。 */
    fun completeFromPreviousHost() {
        telemetry.reset()
        saved["push.auto.attempted"] = false
        saved["push.guide.offered"] = false
        update(PushPermissionState())
    }

    private fun update(value: PushPermissionState) {
        saved["push.guide.visible"] = value.guideVisible
        saved["push.completed"] = value.completed
        saved["push.rating.allowed"] = value.ratingAllowed
        saved["push.request"] = value.request?.name
        current.value = value
    }
}
