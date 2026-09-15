package io.docview.push.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import io.docview.push.host.canSendNotification
import io.docview.push.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 仅在应用可见、通知已授权时建立事件监听 FGS；后台事件不会反复拉起被系统停止的服务。 */
object KeepAliveServiceManager {
    fun startKeepAliveService(context: Context, from: String = "localPush") {
        val app = context.applicationContext
        PushEnvironment.scope.launch(Dispatchers.Main.immediate) {
            if (!PushEnvironment.host.backgroundServiceEnabled) {
                if (CoreService.isRunning) CoreService.stopService(app)
                TriggerCtrl.ensureResidentNotificationExists()
                return@launch
            }
            if (!app.canSendNotification()) {
                if (CoreService.isRunning) CoreService.stopService(app)
                return@launch
            }
            if (CoreService.isRunning) return@launch
            if (!ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(Lifecycle.State.STARTED)) {
                // 常驻通知与 Service 是不同的能力；不能借 Provider 调用绕过系统后台启动限制。
                TriggerCtrl.ensureResidentNotificationExists()
                Logger.d("屏幕监听服务等待前台且通知已授权: from=$from")
                return@launch
            }
            CoreService.startService(app)
        }
    }

    fun isKeepAliveServiceRunning(): Boolean = CoreService.isRunning
}
