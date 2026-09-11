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
)

/** 只记录本次宿主 Activity 生命周期的流程，不持有 Activity。未授权本身不是展示引导的依据。 */
internal class PushPermissionViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val current =
        MutableStateFlow(
            PushPermissionState(
                request = PushPermissionRequest.entries.firstOrNull { it.name == saved.get<String>("push.request") },
                guideVisible = saved["push.guide.visible"] ?: false,
                completed = saved["push.completed"] ?: false,
            )
        )
    val state = current.asStateFlow()

    fun onForeground(granted: Boolean, settingsPending: Boolean = false) {
        if (granted) {
            saved["push.auto.attempted"] = true
            update(PushPermissionState(completed = true))
        } else if (saved.get<Boolean>("push.auto.attempted") != true && !current.value.requesting) {
            saved["push.auto.attempted"] = true
            update(current.value.copy(request = PushPermissionRequest.AUTOMATIC))
        } else if (
            !settingsPending &&
                !current.value.requesting &&
                current.value.request == null &&
                !current.value.guideVisible &&
                !current.value.completed
        ) {
            // 进程重建后 SDK 的运行时回调无法恢复；没有共享设置请求可续接时正常放行，不伪造拒绝。
            update(PushPermissionState(completed = true))
        }
    }

    fun takeRequest(): PushPermissionRequest? {
        val request = current.value.request ?: return null
        update(current.value.copy(request = null, requesting = true))
        return request
    }

    fun onResult(granted: Boolean, denied: Boolean) {
        val showGuide = !granted && denied && saved.get<Boolean>("push.guide.offered") != true
        update(PushPermissionState(guideVisible = showGuide, completed = !showGuide))
    }

    fun guideShown() {
        saved["push.guide.offered"] = true
    }

    fun guideAction(allow: Boolean) {
        // 确认/关闭都消耗本次引导，避免系统设置返回或旋转后形成弹框循环。
        saved["push.guide.offered"] = true
        update(
            PushPermissionState(
                request = if (allow) PushPermissionRequest.GUIDE else null,
                completed = !allow,
            )
        )
    }

    /** 上一宿主已处理过本次权限流程，只跳过重复申请，不把拒绝伪装成授权。 */
    fun completeFromPreviousHost() {
        saved["push.auto.attempted"] = true
        saved["push.guide.offered"] = true
        update(PushPermissionState(completed = true))
    }

    private fun update(value: PushPermissionState) {
        saved["push.guide.visible"] = value.guideVisible
        saved["push.completed"] = value.completed
        saved["push.request"] = value.request?.name
        current.value = value
    }
}
