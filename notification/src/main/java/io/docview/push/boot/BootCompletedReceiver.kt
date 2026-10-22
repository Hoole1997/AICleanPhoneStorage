package io.docview.push.boot

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.docview.push.NotificationRuntimeOwner
import io.docview.push.host.PushEnvironment
import io.docview.push.host.canSendNotification
import io.docview.push.service.CoreService
import io.docview.push.service.KeepAliveServiceManager
import io.docview.push.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** 静态订阅首次解锁后的 BOOT_COMPLETED；不监听锁定开机、不启动页面、不执行扫描或发送普通推送。 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        if (app !is NotificationRuntimeOwner) return
        BootRestoreSession.begin()
        val pending = goAsync()
        val finished = AtomicBoolean()
        val handler = Handler(Looper.getMainLooper())
        var task: Job? = null
        fun finish() { if (finished.compareAndSet(false, true)) pending.finish() }
        // Binder/磁盘等待不可强行中断，独立截止确保广播窗口有界；取消后不再发起 Provider 调用。
        val deadline = Runnable { task?.cancel(); finish(); Logger.w("开机恢复：处理超时，等待下次打开应用") }
        handler.postDelayed(deadline, 9_000L)
        task = PushEnvironment.scope.launch {
            try {
                withTimeout(8_000L) {
                    BootRestoreSession.attempts.withLock {
                        val store = BootRestoreStore(app)
                        val boot = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
                        val reason = BootRestorePolicy.skipReason(store.wasOpened(),
                            PushEnvironment.host.backgroundServiceEnabled, app.canSendNotification(),
                            Build.VERSION.SDK_INT >= 28 && app.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
                            CoreService.isRunning, boot, store.lastAttemptBoot())
                        if (reason != null) { Logger.d("开机恢复：跳过，原因=$reason"); return@withLock }
                        // 先持久化尝试编号；重复广播/进程重建也不能循环强拉。
                        store.recordAttempt(boot)
                        ensureActive()
                        Logger.d("开机恢复：通过通知 Provider 请求恢复常驻前台服务")
                        KeepAliveServiceManager.requestThroughProvider(app, "boot_completed")
                    }
                }
            } catch (_: TimeoutCancellationException) { Logger.w("开机恢复：处理超时，等待下次打开应用") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.e("开机恢复：启动请求失败，等待下次打开应用", error) }
            finally { handler.removeCallbacks(deadline); finish() }
        }
    }
}
