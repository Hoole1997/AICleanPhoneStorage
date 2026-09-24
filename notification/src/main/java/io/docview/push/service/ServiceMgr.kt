package io.docview.push.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import io.docview.push.host.canSendNotification
import io.docview.push.provider.Provider
import io.docview.push.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 宿主开关和通知权限通过后请求事件监听 FGS；启动来源随请求传给 Service。 */
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
            try {
                // Provider 调用可能触发冷启动初始化，放在 IO 线程避免阻塞主线程。
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse("content://${app.packageName}.notification.provider")
                    val extras = Bundle().apply { putString(Provider.EXTRA_FROM, from) }
                    app.contentResolver.call(uri, Provider.METHOD_START_KEEP_ALIVE_SERVICE, null, extras)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("通过通知 Provider 启动屏幕监听服务失败: from=$from", error)
                TriggerCtrl.ensureResidentNotificationExists()
            }
//            if (!ProcessLifecycleOwner.get().lifecycle.currentState
//                    .isAtLeast(Lifecycle.State.STARTED)) {
//                // 常驻通知与 Service 是不同的能力；不能借 Provider 调用绕过系统后台启动限制。
//                TriggerCtrl.ensureResidentNotificationExists()
//                Logger.d("屏幕监听服务等待前台且通知已授权: from=$from")
//                return@launch
//            }
        }
    }

    fun isKeepAliveServiceRunning(): Boolean = CoreService.isRunning
}
