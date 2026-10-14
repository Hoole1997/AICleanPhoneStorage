package io.docview.push.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.docview.push.NotificationRuntimeOwner
import io.docview.push.host.PushEnvironment
import io.docview.push.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/** goAsync 的时限覆盖初始化、检查与实际发布；不启动 Activity/Service 或排队后立即结束。 */
class AlarmWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val owner = context.applicationContext as? NotificationRuntimeOwner ?: return
        val restore = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> true
            AndroidAlarmWakeup.ACTION -> false
            else -> return
        }
        val token = if (restore) null else {
            intent.getStringExtra(AndroidAlarmWakeup.EXTRA_TOKEN)?.takeIf { it.length in 1..64 } ?: return
        }
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        var task: Job? = null
        val handler = Handler(Looper.getMainLooper())
        fun finish() { if (finished.compareAndSet(false, true)) pending.finish() }
        // 独立截止回调保证磁盘/Binder 清理或 IO 排队不会无限占用广播窗口；不是后台轮询。
        val deadline = Runnable {
            task?.cancel()
            finish()
            // 若 IO 队列尚未开始执行原任务，取消前没有 finally；同 token 的短恢复仍会补清凭据。
            if (token != null) PushEnvironment.scope.launch {
                try { owner.notificationRuntime.recoverAlarmWakeup(token) }
                catch (error: Exception) { Logger.e("Alarm 截止状态恢复失败", error) }
            }
        }
        handler.postDelayed(deadline, 9_000L)
        task = PushEnvironment.scope.launch {
            try {
                withTimeout(8_000L) {
                    owner.notificationRuntime.handleAlarmWakeup(token, restore)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.e("Alarm 后台通知检查失败", error) }
            finally {
                // 调度状态回收与广播窗口独立；匹配 token 才恢复，迟到/重复投递不能取消新任务。
                try {
                    if (token != null) withContext(NonCancellable) {
                        owner.notificationRuntime.recoverAlarmWakeup(token)
                    }
                } catch (error: Exception) { Logger.e("Alarm 投递状态恢复失败", error) }
                finally { handler.removeCallbacks(deadline); finish() }
            }
        }
    }
}
