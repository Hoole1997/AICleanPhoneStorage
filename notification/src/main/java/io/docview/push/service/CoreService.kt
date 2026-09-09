package io.docview.push.service

import android.app.Notification
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
        private const val ACTION_START_SERVICE = "io.docview.push.START_KEEP_ALIVE_SERVICE"
        private const val ACTION_STOP_SERVICE = "io.docview.push.STOP_KEEP_ALIVE_SERVICE"
        private const val ACTION_UPDATE_NOTIFICATION = "io.docview.push.UPDATE_FOREGROUND_NOTIFICATION"
        private const val EXTRA_INTERVAL_SECONDS = "interval_seconds"

        private var isServiceRunning = false

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
            val intent = Intent(context, CoreService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }

            try {
                context.startService(intent)
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
    private var intervalSeconds: Long = defaultIntervalSeconds

    override fun onCreate() {
        super.onCreate()
        Logger.d("保活服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                intervalSeconds =
                    intent.getLongExtra(EXTRA_INTERVAL_SECONDS, defaultIntervalSeconds)
                startForegroundService()
            }

            ACTION_STOP_SERVICE -> {
                stopForegroundService()
            }

            ACTION_UPDATE_NOTIFICATION -> {
                updateForegroundNotification()
            }
        }
        return START_STICKY // 服务被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
        Logger.d("保活服务销毁")
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        if (isServiceRunning) {
            Logger.d("保活服务已在运行中，刷新通知，间隔: ${intervalSeconds}秒")
            // 服务已运行，只刷新通知
            updateForegroundNotification()
            return
        }

        // 检查通知权限
        val hasNotificationPermission = canSendNotification()
        Logger.d("通知权限状态: $hasNotificationPermission")

        if (!hasNotificationPermission) {
            Logger.w("没有通知权限，前台服务通知可能不会显示")
        }

        isServiceRunning = true

        // 创建前台通知
        val notification = createForegroundNotification()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)

        // 启动定时任务
        startKeepAliveTask()

        Logger.d("保活服务启动成功，间隔: ${intervalSeconds}秒")
    }

    /**
     * 停止前台服务
     */
    private fun stopForegroundService() {
        if (!isServiceRunning) {
            return
        }

        isServiceRunning = false

        // 停止定时任务
        stopKeepAliveTask()

        // 停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        Logger.d("保活服务停止")
    }

    /**
     * 启动保活任务
     */
    private fun startKeepAliveTask() {
        handler = Handler(Looper.getMainLooper())

        keepAliveRunnable = object : Runnable {
            override fun run() {
                if (!isServiceRunning) {
                    return
                }

                try {
                    Logger.d("执行保活任务")
                    PushEventReporter.reportData("Notific_Pull", mapOf("topic" to "timer"))
                    updateForegroundNotification()

                    EarthquakeController.checkAndTriggerScheduledPush()

                    // 尝试触发保活通知
                    TimingCtrl.getInstance().triggerNotificationIfAllowed(
                        CheckCtrl.NotificationType.KEEPALIVE
                    )

                    Logger.d("保活任务执行完成，下次间隔: ${intervalSeconds}秒")

                } catch (e: Exception) {
                    Logger.e("保活任务执行失败", e)
                }

                // 调度下次执行
                handler?.postDelayed(this, intervalSeconds * 1000)
            }
        }

        // 延迟执行首次任务
        handler?.postDelayed(keepAliveRunnable!!, intervalSeconds * 1000)
    }

    /**
     * 停止保活任务
     */
    private fun stopKeepAliveTask() {
        keepAliveRunnable?.let { runnable ->
            handler?.removeCallbacks(runnable)
        }
        keepAliveRunnable = null
        handler = null
    }

    /**
     * 更新前台服务通知
     */
    private fun updateForegroundNotification() {
        if (!isServiceRunning) {
            Logger.d("前台服务未运行，无法更新通知")
            return
        }

        if (!canSendNotification()) {
            Logger.d("无通知权限，忽略本次更新通知")
            return
        }

        try {
            val newNotification = createForegroundNotification()
            startForeground(NOTIFICATION_ID, newNotification)
            Logger.d("前台服务通知已更新")
        } catch (e: Exception) {
            Logger.e("更新前台服务通知失败", e)
        }
    }

    /**
     * 创建前台通知
     */
    private fun createForegroundNotification(): Notification {
        // 使用TriggerCtrl提供的构建函数
        return TriggerCtrl.buildResidentNotification(this)
    }

}
