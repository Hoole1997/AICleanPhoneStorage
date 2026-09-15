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
    init { PushEnvironment.install(app, host) }

    fun initialize() {
        if (started.getAndSet(true)) return
        io.docview.push.analytics.NotificationVisibility.install(app as Application)
        Utils.init(app)
        PushEnvironment.scope.launch {
            try {
                PushPreferences.initialize(app)
                ResetCtrl.getInstance().initialize(app)
                ConfigCtrl.initialize(app)
                ContentController.initialize(app)
                CheckCtrl.getInstance().initialize(app)
                TriggerCtrl.initializeChannels(app)
                // 清除旧实现的两个固定通知，迁移后只由新模块发布。
                NotificationManagerCompat.from(app).cancel(4101)
                NotificationManagerCompat.from(app).cancel(4102)
                withContext(Dispatchers.Main.immediate) { TimingCtrl.getInstance().initialize(app) }
                // Service 恢复必须等到事件分发器/生命周期状态准备好，再注册第二份监听。
                ready.complete(Unit)
                PushRemoteConfig.initialize()
                ConfigCtrl.initialize(app)
                ContentController.initialize(app)
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
    }

    fun refreshResident() {
        initialize()
        PushEnvironment.scope.launch {
            ready.await()
            TriggerCtrl.triggerResidentNotification()
            // 授权回调也走这里，保证首次前台因无通知权限跳过后可以补建服务。
            io.docview.push.service.KeepAliveServiceManager.startKeepAliveService(app)
        }
    }

    suspend fun awaitReady() { initialize(); ready.await() }
}
