package io.docview.push.scene

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import io.docview.push.NotificationPermissionAccess
import io.docview.push.analytics.NotificationVisibility
import io.docview.push.builder.GeneralNotificationData
import io.docview.push.config.ConfigCtrl
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import io.docview.push.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** CoreService 拥有的场景重复展示适配器；复用配置，独立于旧循环、文案轮播和频控计数。 */
internal class SceneRepeatCoordinator(
    context: Context,
    scope: CoroutineScope,
    private val enabled: (PushScene) -> Boolean,
) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val loop = SceneRepeatLoop(scope, log = Logger::d, policy = ::policy)
    private var registered = false
    private val dismiss = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_DISMISSED) return
            val scene = PushScene.fromCode(intent.getIntExtra(EXTRA_SCENE, -1)) ?: return
            val token = intent.getStringExtra(EXTRA_TOKEN)?.takeIf { it.length <= 64 } ?: return
            Logger.d("场景推送：${scene.logName} 收到划除回调，核验轮次后停止对应循环")
            loop.cancel(scene.notificationId, token)
        }
    }

    init {
        // 只在服务会话内监听自己的 immutable PendingIntent，不需要新 Manifest 入口。
        registered = runCatching {
            ContextCompat.registerReceiver(app, dismiss, IntentFilter(ACTION_DISMISSED).apply {
                addDataScheme("scene-push") // token URI 参与 PendingIntent 身份，也必须纳入广播过滤器。
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        }.getOrDefault(false)
        if (registered) Logger.d("场景推送：已注册新场景划除监听")
        else Logger.w("场景推送：划除监听注册失败，本会话不进行重复发布")
    }

    private fun policy() = SceneRepeatPolicy(
        registered && PushEnvironment.host.repeatNotificationsEnabled && ConfigCtrl.getHoverDurationStrategySwitch() == 1,
        ConfigCtrl.getHoverDurationLoopCount(),
    )

    fun decorate(notification: Notification, scene: PushScene, token: String, iteration: Int = 0): Notification {
        notification.extras.putString(EXTRA_TOKEN, token)
        notification.extras.putInt(EXTRA_ITERATION, iteration)
        notification.deleteIntent = PendingIntent.getBroadcast(app, scene.notificationId,
            Intent(ACTION_DISMISSED).setPackage(app.packageName)
                .setData(Uri.Builder().scheme("scene-push").authority("dismiss").appendPath(scene.code.toString()).appendPath(token).build())
                .putExtra(EXTRA_SCENE, scene.code).putExtra(EXTRA_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return notification
    }

    fun published(event: SceneEvent, model: GeneralNotificationData, token: String, lease: ScenePresentationQueue.Lease? = null) {
        Logger.d("场景推送：${event.scene.logName} 已进入展示周期，后续循环不重复校验原始触发快照")
        loop.start(event.scene.notificationId, token, finished = { lease?.release() }) { completed, current ->
            fun allowed() = current() && lease?.isCurrent != false && enabled(event.scene) && NotificationVisibility.backgroundForDisplay()
            if (!allowed()) { Logger.d("场景推送：${event.scene.logName} 重复前准入未通过，结束本轮"); return@start false }
            // 触发条件只负责首条准入；循环更新的是同一条已展示通知，不重新触发电池/网络/文件事件。
            // 仍复核展示位、开关、前后台、权限、已存在通知和轮次，用户划除后不会复活。
            // 沿用旧节奏：初次发布与第一次重复可响铃，第二次重复起静音；新场景仍不振动。
            val silent = completed > 0
            val channel = if (silent) ScenePublisher.SILENT_CHANNEL else ScenePublisher.CHANNEL
            val notification = withContext(Dispatchers.IO) {
                if (!canNotify(channel)) { Logger.d("场景推送：${event.scene.logName} 重复发布权限或渠道已关闭"); return@withContext null }
                // 只读本 App 已展示的通知。已划除/点击自动取消/被替换后不重新创建卡片。
                val active = manager.activeNotifications.firstOrNull { it.id == event.scene.notificationId }
                    ?: run { Logger.d("场景推送：${event.scene.logName} 系统中通知已不存在，不重新创建"); return@withContext null }
                if (active.notification.extras.getString(EXTRA_TOKEN) != token) { Logger.d("场景推送：${event.scene.logName} 通知已被新轮次替换，旧轮停止"); return@withContext null }
                decorate(TriggerCtrl.sceneNotification(model, channel, useSilent = silent), event.scene, token, completed + 1)
            } ?: return@start false
            currentCoroutineContext().ensureActive()
            if (!allowed() || !canNotify(channel)) return@start false
            manager.notify(event.scene.notificationId, notification)
            Logger.d("场景推送：${event.scene.logName} 重复提交成功，通知ID=${event.scene.notificationId}，额外循环序号=${completed + 1}，声音=${if (silent) "静音" else "遵循普通渠道设置"}")
            // 复用同一文案/点击快照，不重复上报 Show，也不重记场景间隔或旧每日额度。
            true
        }
    }

    private fun canNotify(channel: String) = NotificationPermissionAccess.isGranted(app, ScenePublisher.CHANNEL) &&
        (channel == ScenePublisher.CHANNEL || NotificationPermissionAccess.isGranted(app, channel))

    fun cancelAll() = loop.cancelAll()
    fun reconcile() {
        val policy = policy()
        loop.cancelWhere { id -> !policy.enabled || policy.count <= 0 || PushScene.entries.firstOrNull { it.notificationId == id }?.let(enabled) != true }
    }
    override fun close() {
        loop.close()
        if (registered) { registered = false; runCatching { app.unregisterReceiver(dismiss) } }
    }
    companion object {
        private const val ACTION_DISMISSED = "io.docview.push.scene.DISMISSED"
        private const val EXTRA_SCENE = "scene_repeat_code"
        internal const val EXTRA_TOKEN = "scene_repeat_token"
        internal const val EXTRA_ITERATION = "scene_repeat_iteration"
    }
}
