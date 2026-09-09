package io.docview.push.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.blankj.utilcode.util.Utils
import io.docview.push.check.CheckCtrl
import io.docview.push.controller.BgNotiInterceptController
import io.docview.push.controller.TokenUploadCtrl
import io.docview.push.controller.TriggerCtrl
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.service.MessageService
import io.docview.push.service.KeepAliveServiceManager
import io.docview.push.utils.TopicMgr
import io.docview.push.utils.Logger
import io.docview.push.worker.KeepAliveWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import io.docview.push.host.PushEventReporter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 通知时机控制器
 * 监听锁屏和app切到后台事件，在合适的时机触发通知
 */
class TimingCtrl private constructor() : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "TimingCtrl"

        @Volatile
        private var INSTANCE: TimingCtrl? = null

        fun getInstance(): TimingCtrl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimingCtrl().also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isInitialized = AtomicBoolean(false)
    private val isAppInForeground = AtomicBoolean(false)  // 添加前台状态标识位
    private var context: Context? = null
    private var screenReceiver: ScreenReceiver? = null

    /**
     * 初始化通知时机控制器
     * @param context 上下文
     */
    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) {
            Logger.d("通知时机控制器已经初始化")
            return
        }

        this.context = context.applicationContext
        Logger.d("通知时机控制器初始化开始")

        // 注册锁屏监听
        registerScreenReceiver()

        // 注册应用生命周期监听
        registerAppLifecycleObserver()

        // 启动 WorkManager 保活
        if (io.docview.push.host.PushEnvironment.host.backgroundServiceEnabled) startWorkManagerKeepAlive()

        // 订阅 FCM 主题
        if (io.docview.push.BuildConfig.REMOTE_PUSH_ENABLED) subscribeFCMTopics()

        // 初始化 FCM 服务
        MessageService.initialize()

        // 获取并记录 FCM 令牌
        if (io.docview.push.BuildConfig.REMOTE_PUSH_ENABLED) TopicMgr.getFCMToken { token ->
            if (token != null) {
                TokenUploadCtrl.uploadToken(token)
                Logger.d("FCM 令牌获取成功，长度: ${token.length}")
            } else {
                Logger.w("FCM 令牌获取失败")
            }
        }

        // 记录 FCM 订阅状态
        TopicMgr.logSubscriptionStatus()


        Logger.d("通知时机控制器初始化完成")
    }

    /**
     * 订阅 FCM 主题
     */
    private fun subscribeFCMTopics() {
        try {
            Logger.d("开始订阅 FCM 主题")
            TopicMgr.subscribeCommonTopic()
            Logger.d("FCM 主题订阅完成")
        } catch (e: Exception) {
            Logger.e("订阅 FCM 主题失败", e)
        }
    }

    /**
     * 启动 WorkManager 保活
     */
    private fun startWorkManagerKeepAlive() {
        try {
            // 延迟初始化 WorkManager，确保应用完全启动
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val workManager = WorkManager.getInstance(context!!)

                    // 取消现有的周期性保活工作
                    workManager.cancelUniqueWork("notification_keep_alive")

                    // 创建周期性保活工作（每15分钟执行一次）
                    val periodicWorkRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                        15, TimeUnit.MINUTES
                    ).setInputData(workDataOf("type" to "periodic_keep_alive"))
                        .build()

                    // 提交周期性工作请求
                    workManager.enqueueUniquePeriodicWork(
                        "notification_keep_alive",
                        androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                        periodicWorkRequest
                    )

                    Logger.d("WorkManager 周期性保活已启动（每15分钟执行一次）")
                } catch (e: Exception) {
                    Logger.e("启动 WorkManager 保活失败", e)
                }
            }, 1000) // 延迟1秒执行
        } catch (e: Exception) {
            Logger.e("启动 WorkManager 保活失败", e)
        }
    }

    /**
     * 注册锁屏广播接收器
     */
    private fun registerScreenReceiver() {
        try {
            screenReceiver = ScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context?.let { androidx.core.content.ContextCompat.registerReceiver(it, screenReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
            Logger.d("锁屏监听器注册成功")
        } catch (e: Exception) {
            Logger.e("注册锁屏监听器失败", e)
        }
    }

    /**
     * 注册应用生命周期观察者
     */
    private fun registerAppLifecycleObserver() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            Logger.d("应用生命周期监听器注册成功")
        } catch (e: Exception) {
            Logger.e("注册应用生命周期监听器失败", e)
        }
    }

    /**
     * 应用切到后台
     */
    override fun onStop(owner: LifecycleOwner) = onAppBackground()

    fun onAppBackground() {
        isAppInForeground.set(false)
        Logger.d("应用切到后台")
        if(BgNotiInterceptController.shouldLaunch()){
            triggerNotificationIfAllowed(CheckCtrl.NotificationType.BACKGROUND)
        }
    }

    /**
     * 应用切到前台
     */
    override fun onStart(owner: LifecycleOwner) = onAppForeground()

    fun onAppForeground() {
        isAppInForeground.set(true)
        Logger.d("应用切到前台")
        TriggerCtrl.stopRepeatNotification()
        Utils.getApp().let {
            KeepAliveServiceManager.startKeepAliveService(it)
        }

    }

    /**
     * 检查应用是否在前台
     * @return 是否在前台
     */
    fun isAppInForeground(): Boolean {
        return isAppInForeground.get()
    }

    /**
     * 锁屏广播接收器
     */
    inner class ScreenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Logger.d("屏幕关闭")
                    TriggerCtrl.stopRepeatNotification()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Logger.d("屏幕点亮")
                    context?.let {
                        KeepAliveServiceManager.startKeepAliveService(context)
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    handleUnlock()
                }
            }
        }
    }

    /**
     * 处理解锁事件
     */
    private fun handleUnlock() {
        Logger.d("用户解锁屏幕")
        if (io.docview.push.host.PushEnvironment.host.earthquakeEnabled) EarthquakeController.checkAndTriggerScheduledPush()
        triggerNotificationIfAllowed(CheckCtrl.NotificationType.UNLOCK)
    }

    /**
     * 通用通知触发方法
     * @param type 通知类型
     */
    fun triggerNotificationIfAllowed(type: CheckCtrl.NotificationType) {
        io.docview.push.host.PushEnvironment.scope.launch { triggerOnWorker(type) }
    }

    private fun triggerOnWorker(type: CheckCtrl.NotificationType) {
        val description = when (type) {
            CheckCtrl.NotificationType.UNLOCK -> "解锁通知"
            CheckCtrl.NotificationType.BACKGROUND -> "后台通知"
            CheckCtrl.NotificationType.KEEPALIVE -> "保活通知"
            CheckCtrl.NotificationType.FCM -> "FCM推送通知"
            CheckCtrl.NotificationType.RESIDENT -> ""
            CheckCtrl.NotificationType.EARTHQUAKE -> "地震通知"
        }
        PushEventReporter.reportData("Notific_Pull", mapOf("topic" to "localPush"))

        // 检查是否可以触发通知，并获取具体的拦截原因
        val checkResult = CheckCtrl.getInstance().canTriggerNotificationWithReason(type)
        if (!checkResult.first) {
            val blockReason = checkResult.second
            val reasonString = blockReason?.reason ?: "unknown"
            val reasonDescription = blockReason?.description ?: "未知原因"

            Logger.d("${description}检查未通过，跳过触发 - 原因: ${reasonDescription}")
            PushEventReporter.reportData("Notific_Show_Fail", mapOf(
                "reason" to "app_inner_${type.string}_${reasonString}",
            ))
            return
        }

        Logger.d("触发${description}")
        TriggerCtrl.triggerGeneralNotification(type){
            CheckCtrl.getInstance().recordNotificationTrigger(type)
            CheckCtrl.getInstance().incrementNotificationCount()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            screenReceiver?.let { receiver ->
                context?.unregisterReceiver(receiver)
                screenReceiver = null
            }

            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)

            isInitialized.set(false)
            context = null

            Logger.d("通知时机控制器已释放")
        } catch (e: Exception) {
            Logger.e("释放通知时机控制器失败", e)
        }
    }

}
