package io.docview.push

import android.app.Application
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.Utils
import io.docview.push.check.CheckCtrl
import io.docview.push.config.ConfigCtrl
import io.docview.push.config.ContentController
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import io.docview.push.host.PushPreferences
import io.docview.push.host.PushRemoteConfig
import io.docview.push.timing.TimingCtrl
import io.docview.push.utils.ResetCtrl
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** 生命周期适配器；推送实际执行仍使用迁入的 ConfigCtrl/CheckCtrl/TimingCtrl/TriggerCtrl。 */
class NotificationRuntime(context: Context, host: NotificationHost) {
    private val app = context.applicationContext
    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val audienceReady = CompletableDeferred<Unit>()
    private val alarms by lazy {
        io.docview.push.alarm.AlarmWakeupCoordinator(app, PushEnvironment.host::alarmWakeupConfig,
            awaitAudience = { withTimeoutOrNull(500L) { audienceReady.await() }; Unit },
        ) { interval, canSend ->
            TimingCtrl.getInstance().triggerNotificationAndAwait(
                CheckCtrl.NotificationType.ALARM_MANAGER, interval, canSend,
            )
        }
    }
    // 捕获首次进程启动所在自然日，避免 IO 排队跨午夜后误记为次日；不使用安装时间。
    private val firstObservedEpochDay = java.time.LocalDate.now().toEpochDay()
    init { PushEnvironment.install(app, host) }

    fun initialize() {
        if (started.getAndSet(true)) return
        io.docview.push.analytics.NotificationVisibility.install(app as Application)
        Utils.init(app)
        PushEnvironment.scope.launch {
            try {
                PushPreferences.initialize(app)
                ContentController.recordFirstLaunch(app, firstObservedEpochDay)
                ResetCtrl.getInstance().initialize(app)
                ConfigCtrl.initialize(app)
                PushEnvironment.host.awaitContentLanguage()
                ContentController.initialize(app, firstObservedEpochDay)
                CheckCtrl.getInstance().initialize(app)
                TriggerCtrl.initializeChannels(app)
                // 清除旧实现的两个固定通知，迁移后只由新模块发布。
                NotificationManagerCompat.from(app).cancel(4101)
                NotificationManagerCompat.from(app).cancel(4102)
                val alarmCoordinator = alarms // SharedPreferences 首次加载离开主线程。
                withContext(Dispatchers.Main.immediate) {
                    TimingCtrl.getInstance().initialize(app)
                    alarmCoordinator.start()
                }
                // Service 恢复必须等到事件分发器/生命周期状态准备好，再注册第二份监听。
                ready.complete(Unit)
                refreshAlarmSchedule()
                PushRemoteConfig.initialize()
                ConfigCtrl.initialize(app)
                ContentController.initialize(app, firstObservedEpochDay)
                refreshAlarmSchedule()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                ready.completeExceptionally(error)
                android.util.Log.e("CleanPush", "Notification initialization failed", error)
            }
        }
    }

    /** 宿主归因与推送配置共用渠道；不能一直停留在构建时的默认 natural。 */
    fun setPaidUser(paid: Boolean) {
        io.docview.push.host.PushUserChannel.setChannel(if (paid)
            io.docview.push.host.PushUserChannel.UserChannelType.PAID
            else io.docview.push.host.PushUserChannel.UserChannelType.NATURAL)
        audienceReady.complete(Unit)
        initialize()
        PushEnvironment.scope.launch {
            try {
                ready.await()
                if (io.docview.push.host.PushUserChannel.isConfirmedPaidUser()) {
                    // 归因可能晚于通知模块初始化；确认买量后再加载分池，不依赖重启或下一次冷启动。
                    ContentController.initialize(app, firstObservedEpochDay)
                } else withContext(Dispatchers.Main.immediate) {
                    if (!io.docview.push.host.PushUserChannel.isConfirmedPaidUser()) {
                        TriggerCtrl.cancelDayPoolNotification()
                    }
                }
                alarms.refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("CleanPush", "Unable to update day-pool eligibility", error)
            }
        }
    }

    fun refreshResident() {
        initialize()
        PushEnvironment.scope.launch {
            ready.await()
            TriggerCtrl.triggerResidentNotification()
            // 授权回调也走这里，保证首次前台因无通知权限跳过后可以补建服务。
            io.docview.push.service.KeepAliveServiceManager.startKeepAliveService(app, from = "resident_refresh")
        }
    }

    suspend fun awaitReady() { initialize(); ready.await() }

    /** Remax 激活回调和前台恢复共用调度入口；配置读取与注册不占用主线程。 */
    fun refreshAlarmSchedule() {
        initialize()
        PushEnvironment.scope.launch {
            try { ready.await(); withTimeoutOrNull(500L) { audienceReady.await() }; alarms.refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("CleanPush", "Unable to refresh alarm schedule", error) }
        }
    }

    /** Receiver 的有界任务等待实际发布结束；重启/更新只恢复调度，不能直接展示通知。 */
    internal suspend fun handleAlarmWakeup(token: String?, restore: Boolean) {
        awaitReady()
        // 宿主恢复已保存的渠道通常只需一次 IO；未能确认时按 standard tier，不能猜测买量归因。
        withTimeoutOrNull(500L) { audienceReady.await() }
        if (!restore && io.docview.push.host.PushUserChannel.isConfirmedPaidUser() && !ContentController.isInitialized()) {
            ContentController.initialize(app, firstObservedEpochDay)
        }
        if (restore) alarms.refresh(force = true)
        else if (token != null) alarms.deliver(token)
    }

    /** 不依赖 ready 成功，避免冷初始化失败后留下已消费的 Alarm 注册标记。 */
    internal suspend fun recoverAlarmWakeup(token: String) = alarms.recoverDelivery(token)
}
