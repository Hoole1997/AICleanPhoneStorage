package io.docview.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import io.docview.push.NotificationDestination
import io.docview.push.NotificationRuntimeOwner
import io.docview.push.host.PushEnvironment
import kotlinx.coroutines.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.docview.push.check.CheckCtrl
import io.docview.push.controller.TriggerCtrl
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.timing.TimingCtrl
import io.docview.push.utils.Logger
import io.docview.push.host.PushLongPreference
import io.docview.push.host.canSendNotification
import io.docview.push.host.PushEventReporter

/**
 * 前台保活服务
 * 用于定期触发通知保活机制
 */
class CoreService : Service() {

    companion object {
        private val NOTIFICATION_ID = TriggerCtrl.getResidentNotificationId()

        // 默认15分钟 = 900秒
        private const val DEFAULT_INTERVAL_SECONDS = 900L

        // 持久化存储默认间隔时间
        var defaultIntervalSeconds by PushLongPreference("notification_keep_alive_default_interval", DEFAULT_INTERVAL_SECONDS)

        // 服务控制参数
        private const val ACTION_START_SERVICE = CoreServiceCommand.ACTION_START
        private const val ACTION_UPDATE_NOTIFICATION = CoreServiceCommand.ACTION_UPDATE
        private const val EXTRA_INTERVAL_SECONDS = "interval_seconds"
        /**
         * 设置默认间隔时间
         * @param seconds 间隔时间（秒）
         */
        fun setDefaultIntervalSeconds1(seconds: Long) {
            defaultIntervalSeconds = seconds
            Logger.d("设置通知保活默认轮训间隔时间: ${seconds}秒")
        }

        /**
         * 启动保活服务
         * @param context 上下文
         * @param intervalSeconds 间隔时间（秒），默认使用持久化存储的值
         */
        fun startService(context: Context, intervalSeconds: Long = defaultIntervalSeconds) {
            if (!io.docview.push.host.PushEnvironment.host.backgroundServiceEnabled) {
                TriggerCtrl.ensureResidentNotificationExists()
                return
            }
            val intent = Intent(context, CoreService::class.java).apply {
                action = ACTION_START_SERVICE
                putExtra(EXTRA_INTERVAL_SECONDS, intervalSeconds)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Logger.d("启动保活服务，间隔: ${intervalSeconds}秒")
            } catch (e: Throwable) {
                PushEventReporter.reportData("Notific_Show_Fail",mapOf("reason" to "alive_service_${e.message}"))
                Logger.e("启动保活服务失败", e)
            }
        }

        /**
         * 停止保活服务
         * @param context 上下文
         */
        fun stopService(context: Context) {
            // 停止请求直接交给系统，不能为了停止而启动一个新的 Service。
            try {
                context.stopService(Intent(context, CoreService::class.java))
                Logger.d("停止保活服务")
            } catch (e: Exception) {
                Logger.e("停止保活服务失败", e)
            }
        }


        /**
         * 更新前台服务通知
         * @param context 上下文
         */
        fun updateNotification(context: Context) {
            val intent = Intent(context, CoreService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }

            try {
                context.startService(intent)
                Logger.d("前台服务通知更新请求已发送")
            } catch (e: Throwable) {
                Logger.e("更新前台服务通知失败", e)
            }
        }
    }

    private var handler: Handler? = null
    private var keepAliveRunnable: Runnable? = null
    private var intervalSeconds = DEFAULT_INTERVAL_SECONDS
    private var requestedIntervalSeconds: Long? = null
    private var runtimeReady = false
    private var initialization: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = CoreServiceLifecycle(
        promote = {
            startForeground(NOTIFICATION_ID,
                if (runtimeReady) TriggerCtrl.buildResidentNotification(this) else bootstrapNotification())
        },
        beginWork = ::restoreWork,
        cancelWork = { initialization?.cancel(); initialization = null; stopKeepAliveTask() },
        leaveForeground = { stopForeground(STOP_FOREGROUND_REMOVE) },
        stopService = { stopSelf() },
        reportFailure = ::reportServiceFailure,
    )

    override fun onCreate() {
        super.onCreate()
        // Android 对已经接收的 startForegroundService 请求要求先晋升；直接 stopSelf 也可能被判启动失败。
        // 这里只履行平台契约，不启动保活任务；开关关闭时 onStartCommand 随即停止。
        session.prepareForeground()
        Logger.d("保活服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            // 检查放在 Service 内部，系统 sticky 恢复也必须遵守当前宿主开关。
            if (!PushEnvironment.host.backgroundServiceEnabled) {
                session.stop()
                return START_NOT_STICKY
            }
            val running = when (CoreServiceCommand.from(intent == null, intent?.action)) {
                CoreServiceCommand.START, CoreServiceCommand.RESTORE -> {
                    requestedIntervalSeconds = if (intent?.hasExtra(EXTRA_INTERVAL_SECONDS) == true)
                        intent.getLongExtra(EXTRA_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS) else null
                    if (runtimeReady) intervalSeconds = effectiveInterval()
                    session.start(enabled = true)
                }
                CoreServiceCommand.UPDATE -> session.refresh()
                CoreServiceCommand.STOP, CoreServiceCommand.UNKNOWN -> { session.stop(); false }
            }
            if (running) START_STICKY else START_NOT_STICKY
        } catch (error: Exception) {
            session.fail(error)
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        session.stop()
        serviceScope.cancel()
        super.onDestroy()
        Logger.d("保活服务销毁")
    }

    // specialUse 当前没有六小时预算。仍响应系统的停止回调，不在回调中启动服务或等待异步清理。
    override fun onTimeout(startId: Int) {
        Logger.w("前台服务收到停止超时回调: startId=$startId")
        session.stop()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Logger.w("前台服务收到停止超时回调: startId=$startId, type=$fgsType")
        session.stop()
    }

    /** 冷进程先同步建立最小前台通知，不等待配置 I/O/网络或依赖尚未初始化的 TriggerCtrl。 */
    private fun bootstrapNotification(): Notification {
        val host = PushEnvironment.host
        require(host.smallIcon != 0) { "Missing foreground notification icon" }
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(TriggerCtrl.CHANNEL_ID_RESIDENT, host.residentChannelName, NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, TriggerCtrl.CHANNEL_ID_RESIDENT)
            .setSmallIcon(host.smallIcon)
            .setContentTitle(host.appName)
            .setContentIntent(host.contentIntent(NotificationDestination.HOME))
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).build()
    }

    private fun restoreWork() {
        initialization = serviceScope.launch {
            try {
                (application as NotificationRuntimeOwner).notificationRuntime.awaitReady()
                ensureActive()
                if (!session.running) return@launch
                if (!PushEnvironment.host.backgroundServiceEnabled) { session.stop(); return@launch }
                runtimeReady = true
                // preferences 预载完成后再读间隔，避免进程重建时误用缓存尚未就绪的默认值。
                intervalSeconds = effectiveInterval()
                if (session.refresh()) startKeepAliveTask()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { session.fail(error) }
        }
    }

    private fun effectiveInterval(): Long = (requestedIntervalSeconds ?: defaultIntervalSeconds)
        .takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 } ?: DEFAULT_INTERVAL_SECONDS

    private fun reportServiceFailure(error: Exception) {
        Logger.e("前台服务启动/恢复失败，停止当前实例", error)
        PushEventReporter.reportData("Notific_Show_Fail",
            mapOf("reason" to "alive_service_${error.javaClass.simpleName}"))
    }

    private fun startKeepAliveTask() {
        stopKeepAliveTask()
        handler = Handler(Looper.getMainLooper())
        keepAliveRunnable = object : Runnable {
            override fun run() {
                if (!session.running) return
                try {
                    // 晋升/刷新失败会同步终止会话，不能继续触发通知或重新安排下一轮。
                    if (!PushEnvironment.host.backgroundServiceEnabled) { session.stop(); return }
                    if (canSendNotification() && !session.refresh()) return
                    Logger.d("执行保活任务")
                    PushEventReporter.reportData("Notific_Pull", mapOf("topic" to "timer"))
                    EarthquakeController.checkAndTriggerScheduledPush()
                    TimingCtrl.getInstance().triggerNotificationIfAllowed(CheckCtrl.NotificationType.KEEPALIVE)
                } catch (error: Exception) {
                    Logger.e("保活任务执行失败", error)
                }
                if (session.running) handler?.postDelayed(this, intervalSeconds * 1000)
            }
        }
        handler?.postDelayed(keepAliveRunnable!!, intervalSeconds * 1000)
    }

    private fun stopKeepAliveTask() {
        keepAliveRunnable?.let { handler?.removeCallbacks(it) }
        keepAliveRunnable = null
        handler = null
    }
}
