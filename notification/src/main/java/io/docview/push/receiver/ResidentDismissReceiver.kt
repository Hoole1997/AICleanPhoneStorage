package io.docview.push.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.docview.push.NotificationRuntimeOwner
import io.docview.push.controller.ResidentNotificationDismissal
import io.docview.push.host.PushEnvironment
import io.docview.push.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 系统执行常驻通知的 deleteIntent 时进入；每次只处理一个有期限的恢复任务。 */
class ResidentDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ResidentNotificationDismissal.ACTION) return
        val app = context.applicationContext
        val runtime = (app as? NotificationRuntimeOwner)?.notificationRuntime ?: return
        val pending = goAsync()
        Logger.d("[resident_delete] received")
        // 立即建立超时，再把构建切到 IO；不能让繁忙 IO 队列耗尽广播的处理窗口。
        PushEnvironment.scope.launch(Dispatchers.Main.immediate) {
            try {
                // 冷进程也等待本地初始化完成；不在 onReceive 主线程阻塞，不等待远程网络配置。
                withTimeout(5_000) {
                    runtime.awaitReady()
                    ResidentNotificationDismissal.restoreIfServiceRunning(app)
                }
            } catch (_: TimeoutCancellationException) {
                Logger.w("[resident_delete] restore timed out")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("[resident_delete] restore failed", error)
            } finally {
                pending.finish()
            }
        }
    }
}
