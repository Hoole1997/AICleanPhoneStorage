package io.docview.push.controller

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import io.docview.push.R
import io.docview.push.builder.EarthquakeModelManager
import io.docview.push.builder.GeneralModelManager
import io.docview.push.builder.GeneralNotificationData
import io.docview.push.builder.NotificationType
import io.docview.push.builder.ResidentModelManger
import io.docview.push.builder.type2notificationId
import io.docview.push.config.ConfigCtrl
import io.docview.push.check.CheckCtrl
import io.docview.push.earthquake.EarthquakeInfo
import io.docview.push.utils.Logger
import io.docview.push.receiver.DeleteReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.docview.push.host.canSendNotification
import io.docview.push.host.PushEventReporter

/**
 * 通知触发控制器
 * 提供常驻通知和普通通知的触发功能
 */
@SuppressLint("StaticFieldLeak", "MissingPermission")
object TriggerCtrl {

    const val CHANNEL_ID_RESIDENT = "resident_notification"
    const val CHANNEL_ID_GENERAL = "general_notification"
    const val CHANNEL_ID_GENERAL_SILENT = "general_silent_notification"
    const val CHANNEL_NAME_RESIDENT = "recovery_resident"
    const val CHANNEL_NAME_GENERAL = "recovery_single"
    const val CHANNEL_NAME_GENERAL_SILENT = "recovery_loop"

    private var notificationManager: NotificationManagerCompat? = null
    private var context: Context? = null

    // 全局协程作用域，用于异步构建通知
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 重复通知相关（全局单例）
    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var repeatCount: Int = 0
    private var currentNotificationId: Int = 0

    /**
     * 初始化通知通道
     * @param context 上下文
     */
    fun initializeChannels(context: Context) {
        this.context = context.applicationContext
        this.notificationManager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT < 26) return

        // 常驻通知通道
        val residentChannel = NotificationChannel(
            CHANNEL_ID_RESIDENT, io.docview.push.host.PushEnvironment.host.residentChannelName, NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "for resident notification"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        // 普通通知通道
        val generalChannel = NotificationChannel(
            CHANNEL_ID_GENERAL, io.docview.push.host.PushEnvironment.host.pushChannelName, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "for general notification"
            setShowBadge(true)
            enableLights(false)
            enableVibration(false)
        }

        // 静音通知通道
        val silentChannel = NotificationChannel(
            CHANNEL_ID_GENERAL_SILENT,
            CHANNEL_NAME_GENERAL_SILENT,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "for silent notification (repeat notifications)"
            setShowBadge(true)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)  // 设置为静音
        }

        notificationManager?.createNotificationChannels(
            listOf(
                residentChannel, generalChannel, silentChannel
            )
        )
        Logger.d("通知通道创建完成")
    }


    /**
     * 通用通知触发方法
     * @param notificationType 通知类型
     * @param modelBuilder 通知数据构建函数
     * @param notificationBuilder 通知对象构建函数
     * @param onNotificationSent 通知发送完成回调
     */
    private fun triggerNotification(
        notificationType: String,
        modelBuilder: suspend (Context) -> GeneralNotificationData,
        notificationBuilder: (GeneralNotificationData) -> Notification,
        onNotificationSent: ((GeneralNotificationData, Notification) -> Unit)? = null
    ) {
        val context = context ?: return
        val notificationManager = notificationManager ?: return

        // 使用全局协程异步构建通知
        notificationScope.launch {
            try {
                // 在 IO 线程中构建通知数据
                val notificationData = withContext(Dispatchers.IO) {
                    modelBuilder(context)
                }

                // 在 IO 线程中构建通知对象
                val notification = withContext(Dispatchers.IO) {
                    notificationBuilder(notificationData)
                }

                // 切换到主线程执行 notify
                withContext(Dispatchers.Main) {
                    if (!io.docview.push.NotificationPermissionAccess.isGranted(context,
                            if (Build.VERSION.SDK_INT >= 26) notification.channelId else null)) return@withContext
                    try {
                        notificationManager.notify(notificationData.notificationId, notification)
                    } catch (_: SecurityException) { return@withContext }
                    Logger.d("${notificationType}发送成功，ID: ${notificationData.notificationId}")

                    // 调用通知完成回调
                    onNotificationSent?.invoke(notificationData, notification)
                }

            } catch (e: Exception) {
                Logger.e("发送${notificationType}失败", e)
            }
        }
    }

    /**
     * 检查是否应该启用重复通知
     * @return 是否启用重复通知
     */
    private fun shouldEnableRepeatNotification(): Boolean {
        if (!io.docview.push.host.PushEnvironment.host.repeatNotificationsEnabled) return false
        val configController = ConfigCtrl

        // 检查是否开启重复通知策略
        if (configController.getHoverDurationStrategySwitch() != 1) {
            return false
        }

        val loopCount = configController.getHoverDurationLoopCount()
        if (loopCount <= 0) {
            return false
        }

        return true
    }

    /**
     * 获取重复通知的循环次数
     * @return 循环次数
     */
    private fun getRepeatLoopCount(): Int {
        return ConfigCtrl.getHoverDurationLoopCount()
    }

    /**
     * 检查并启动重复通知
     * @param notificationId 通知ID
     * @param notification 通知对象
     */
    private fun checkAndStartRepeatNotification(notificationData: GeneralNotificationData,
                                                notification: Notification,
                                                type: CheckCtrl.NotificationType) {
        // 检查是否应该启用重复通知
        if (!shouldEnableRepeatNotification()) {
            return
        }

        val loopCount = getRepeatLoopCount()

        // 停止之前的重复任务
        stopRepeatNotification()

        Logger.d("开始重复通知，ID: ${notificationData.notificationId}，循环次数: $loopCount")

        // 初始化重复计数
        repeatCount = 0
        currentNotificationId = notificationData.notificationId

        // 创建 Handler
        repeatHandler = Handler(Looper.getMainLooper())

        // 创建重复任务
        repeatRunnable = object : Runnable {
            override fun run() {
                val maxCount = getRepeatLoopCount()

                if (repeatCount < maxCount) {
                    // 所有重复通知都使用静音通道
                    val notificationToSend = buildGeneralNotification(notificationData, useSilent = true)

                    // 执行重复通知
                    notificationManager?.notify(currentNotificationId, notificationToSend)
                    Logger.d("重复通知执行，ID: $currentNotificationId，第${repeatCount + 1}次，使用静音通道")

                    // 增加计数
                    repeatCount++

                    // 4秒后再次执行
                    repeatHandler?.postDelayed(this, 4000)
                } else {
                    // 达到最大次数，停止重复
                    stopRepeatNotification()
                    Logger.d("重复通知完成，ID: $currentNotificationId，总次数: $maxCount")
                }
            }
        }

        // 4秒后开始第一次重复
        repeatHandler?.postDelayed(repeatRunnable!!, 4000)
    }

    /**
     * 停止重复通知
     */
    fun stopRepeatNotification() {
        repeatHandler?.removeCallbacks(repeatRunnable ?: return)
        repeatHandler = null
        repeatRunnable = null
        repeatCount = 0
        currentNotificationId = 0

        Logger.d("停止重复通知")
    }

    fun triggerResidentNotification() {
        if (context?.canSendNotification() == true) {
            triggerNotification(
                notificationType = "常驻通知",
                modelBuilder = { context -> ResidentModelManger().getModel(context) },
                notificationBuilder = { data -> resident(data) },
                onNotificationSent = { notificationData, notification ->
                    residentTrack(notificationData)
                })
        }
    }

    fun triggerEarthquakeNotification(
        type: CheckCtrl.NotificationType,
        earthquake:EarthquakeInfo,){
        if (context?.canSendNotification() == true) {
            triggerNotification(
                notificationType = "地震通知",
                modelBuilder = { context -> EarthquakeModelManager().getModel(context,earthquake,type) },
                notificationBuilder = { data -> general(data) },
                onNotificationSent = { notificationData, notification ->
                    generalTrack(type,notificationData)
                })
        }
    }

    fun triggerGeneralNotification(
        type: CheckCtrl.NotificationType, onNotificationSent: (() -> Unit)? = null
    ) {
        if (context?.canSendNotification() == true) {
            triggerNotification(
                notificationType = "常规通知",
                modelBuilder = { context -> GeneralModelManager().getModel(context, type) },
                notificationBuilder = { data -> general(data) },
                onNotificationSent = { notificationData, notification ->
                    onNotificationSent?.invoke()
                    generalTrack(type, notificationData)
                    // 检查是否需要重复通知
                    checkAndStartRepeatNotification(notificationData, notification,type)
                })
        }
    }

    private fun resident(model: GeneralNotificationData) =
        NotificationCompat.Builder(context!!, CHANNEL_ID_RESIDENT)
            .setSmallIcon(io.docview.push.host.PushEnvironment.host.smallIcon)
            .setContentTitle(model.contentTitle)
            .setContentIntent(model.contentIntent)
            .setCustomContentView(model.contentView)
            .setCustomBigContentView(model.bigContentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOnlyAlertOnce(true).setAutoCancel(false).setShowWhen(false)
            .setOngoing(true).setSilent(true).build()

    //消息置顶
    private fun generateMessageStyle( model: GeneralNotificationData): NotificationCompat.MessagingStyle {
        return NotificationCompat.MessagingStyle(Person.Builder().setName(" ").build())
            .addMessage(
                model.contentTitle,
                System.currentTimeMillis(),
                Person.Builder()
                    .setName(model.contentTitle)
                    .also { build ->
                        val icon = io.docview.push.host.PushEnvironment.host.smallIcon
                        build.setIcon(IconCompat.createWithResource(context!!, icon))
                    }
                    .build()
            )
    }

    /**
     * 构建通知对象，可指定是否使用静音通道
     * 使用 BigContentView 作为主要显示，通过 PRIORITY_HIGH 强制展开
     * @param model 通知数据
     * @param useSilent 是否使用静音通道
     */
    private fun buildGeneralNotification(model: GeneralNotificationData, useSilent: Boolean): Notification {
        val channelId = if (useSilent) {
            CHANNEL_ID_GENERAL_SILENT  // 使用静音通道
        } else {
            CHANNEL_ID_GENERAL // 使用普通通道
        }

        // 创建删除监听 Intent
        val deleteIntent = Intent(context, DeleteReceiver::class.java).apply {
            action = "io.docview.push.ACTION_NOTIFICATION_DELETE"
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 13+ (API 33) 强制使用大布局，低版本使用原始策略
        val useBigLayout = Build.VERSION.SDK_INT >= 33

        val builder = if (useBigLayout) {
            // Android 13+ 强制使用大布局
            NotificationCompat.Builder(context!!, channelId)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(context!!, R.color.noti_color))
                .setSmallIcon(io.docview.push.host.PushEnvironment.host.smallIcon)
                .setAutoCancel(true)
                .setGroup("push_" + System.currentTimeMillis())
                .setContentTitle(model.contentTitle)
                .setContentText(model.contentContent)
                .setContentIntent(model.contentIntent)
                .setDeleteIntent(deletePendingIntent)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(model.contentView)
                .setCustomBigContentView(model.bigContentView)
                .setCustomHeadsUpContentView(model.bigContentView)
        } else {
            // Android 13 以下使用原始策略
            NotificationCompat.Builder(context!!, channelId)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(ContextCompat.getColor(context!!, R.color.noti_color))
                .setSmallIcon(io.docview.push.host.PushEnvironment.host.smallIcon)
                .setAutoCancel(true)
                .setGroup("push_" + System.currentTimeMillis())
                .setContentText(model.contentTitle)
                .setContentIntent(model.contentIntent)
                .setDeleteIntent(deletePendingIntent)
                .setCustomContentView(model.contentView)
                .setCustomBigContentView(model.bigContentView)
                .apply { generateMessageStyle(model) }
                .apply {
                    if (Build.VERSION.SDK_INT >= 31) {
                        setCustomHeadsUpContentView(model.contentView)
                    }
                }
        }

        Logger.d("构建通知，使用${if (useSilent) "静音" else "普通"}通道: $channelId，${if (useBigLayout) "大布局(Android 13+)" else "原始策略"}")

        return builder.build()
    }

    private fun general(model: GeneralNotificationData): Notification {
        // 首次通知总是使用普通通道（有声音）
        return buildGeneralNotification(model, useSilent = true)
    }

    /**
     * 取消通知
     * @param notificationId 通知ID
     */
    fun cancelNotification(notificationId: Int) {
        // 停止重复通知
        stopRepeatNotification()

        // 取消通知
        notificationManager?.cancel(notificationId)
        Logger.d("通知已取消，ID: $notificationId")
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        // 停止重复通知
        stopRepeatNotification()

        // 取消所有通知
        notificationManager?.cancelAll()
        Logger.d("所有通知已取消")
    }

    /**
     * 检查并确保常驻通知存在
     * 如果通知中心不存在常驻通知，则触发常驻通知
     */
    @SuppressLint("MissingPermission")
    fun ensureResidentNotificationExists() {
        val notificationManager = notificationManager ?: return
        val JUNKNotificationId = type2notificationId[NotificationType.JUNK] ?: 0

        // 使用协程异步执行
        notificationScope.launch {
            try {
                // 检查通知中心是否存在指定ID的通知
                val activeNotifications = notificationManager.activeNotifications
                val hasResidentNotification =
                    activeNotifications.any { it.id == JUNKNotificationId }

                if (!hasResidentNotification) {
                    Logger.d("通知中心不存在常驻通知(ID: $JUNKNotificationId)，尝试触发常驻通知")
                    triggerResidentNotification()
                } else {
                    Logger.d("通知中心已存在常驻通知(ID: $JUNKNotificationId)")
                }
            } catch (e: Exception) {
                Logger.e("检查常驻通知状态失败", e)
                // 检查失败时，为了安全起见，触发常驻通知
                triggerResidentNotification()
            }
        }
    }


    /**
     * 获取常驻通知的ID
     * @return 常驻通知ID
     */
    fun getResidentNotificationId(): Int = type2notificationId[NotificationType.JUNK] ?: 0

    /**
     * 构建常驻通知
     * @param context 上下文
     * @return 常驻通知对象
     */
    fun buildResidentNotification(context: Context): Notification {
        val residentModel = ResidentModelManger().getModel(context)
        if(context.canSendNotification()){
            residentTrack(residentModel)
        }
        return resident(residentModel)
    }

    private fun residentTrack(residentModel: GeneralNotificationData) {
        PushEventReporter.reportData(
            "Notific_Show", mapOf(
                "Notific_Type" to 4,
                "Notific_Position" to 2,
                "Notific_Priority" to "PRIORITY_DEFAULT",
                "event_id" to "permanent",
//                "title" to residentModel.contentTitle,
//                "text" to residentModel.contentContent,
            )
        )
    }

    private fun generalTrack(
        type: CheckCtrl.NotificationType,
        notificationData: GeneralNotificationData
    ) {
        PushEventReporter.reportData(
            "Notific_Show", mapOf(
                "Notific_Type" to when (type) {
                    CheckCtrl.NotificationType.UNLOCK -> 1
                    CheckCtrl.NotificationType.BACKGROUND -> 1
                    CheckCtrl.NotificationType.KEEPALIVE -> 1
                    CheckCtrl.NotificationType.FCM -> 3
                    CheckCtrl.NotificationType.RESIDENT -> 4
                    CheckCtrl.NotificationType.EARTHQUAKE -> 5
                },
                "Notific_Position" to 1,
                "Notific_Priority" to "PRIORITY_MAX",
                "event_id" to "customer_general_style",
                "title" to notificationData.contentTitle,
                "text" to notificationData.contentContent,
            )
        )
    }
}
