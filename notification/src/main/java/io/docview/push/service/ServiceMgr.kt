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
import kotlinx.coroutines.ensureActive
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
            val background = io.docview.push.analytics.NotificationVisibility.backgroundForDisplay()
            if (!io.docview.push.boot.BootRestoreSession.allowNormalStart(background)) return@launch
            // 只约束新建服务，不拦截上面的停用/撤权清理。冷进程的数据刷新没有后台启动资格；
            // 开机由专用 Receiver 单次请求，正常前台、FCM 等原有显式来源保留自己的入口。
            if (from == "resident_refresh" && background) return@launch
            try {
                requestThroughProvider(app, from)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("通过通知 Provider 启动屏幕监听服务失败: from=$from", error)
                TriggerCtrl.ensureResidentNotificationExists()
            }
        }
    }

    /** 可等待的同一 Provider 通道；广播必须持有 goAsync 到调用结束，不另起脱离生命周期的任务。 */
    internal suspend fun requestThroughProvider(context: Context, from: String) = withContext(Dispatchers.IO) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val uri = Uri.parse("content://${context.packageName}.notification.provider")
        val extras = Bundle().apply { putString(Provider.EXTRA_FROM, from) }
        context.contentResolver.call(uri, Provider.METHOD_START_KEEP_ALIVE_SERVICE, null, extras)
        Unit
    }

    fun isKeepAliveServiceRunning(): Boolean = CoreService.isRunning
}
