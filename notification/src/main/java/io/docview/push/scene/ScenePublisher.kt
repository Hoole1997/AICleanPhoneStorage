package io.docview.push.scene

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import io.docview.push.NotificationPermissionAccess
import io.docview.push.R
import io.docview.push.analytics.NotificationContent
import io.docview.push.analytics.NotificationTriggerSource
import io.docview.push.analytics.NotificationVisibility
import io.docview.push.builder.*
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import io.docview.push.host.PushEventReporter
import io.docview.push.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 服务注入其拥有的循环会话；独立布局渲染/测试不创建无人管理的后台重复任务。 */
internal class ScenePublisher(context: Context, private val repeats: SceneRepeatCoordinator? = null) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    suspend fun publish(event: SceneEvent, copy: SceneSelection, lease: ScenePresentationQueue.Lease? = null, canSend: () -> Boolean): Boolean {
        check(lease == null || repeats != null) // 展示位必须交给服务拥有的循环释放，不能遗留永久占用。
        withContext(Dispatchers.IO) { ensureChannel() }
        if (!NotificationPermissionAccess.isGranted(app, CHANNEL)) { Logger.d("场景推送：${event.scene.logName} 通知权限或渠道未开启，本次不发布"); return false }
        Logger.d("场景推送：${event.scene.logName} 出队条件通过，开始构建单句富文本通知，通知ID=${event.scene.notificationId}")
        val model = withContext(Dispatchers.IO) { model(event, copy) }
        return withContext(Dispatchers.Main.immediate) {
            ensureChannel()
            if (!canSend() || lease?.isCurrent == false || !NotificationVisibility.backgroundForDisplay() || !NotificationPermissionAccess.isGranted(app, CHANNEL)) {
                Logger.d("场景推送：${event.scene.logName} 发布前复核未通过，条件、展示位、前后台或权限已变化")
                return@withContext false
            }
            val notification = TriggerCtrl.sceneNotification(model, CHANNEL)
            val token = java.util.UUID.randomUUID().toString()
            repeats?.decorate(notification, event.scene, token)
            try { manager.notify(event.scene.notificationId, notification) }
            catch (_: SecurityException) { Logger.w("场景推送：${event.scene.logName} 系统拒绝发布通知，权限已变化"); return@withContext false }
            Logger.d("场景推送：${event.scene.logName} 首条通知已提交系统，通知ID=${event.scene.notificationId}")
            repeats?.published(event, model, token, lease)
            // notify 已成功后，埋点出口失败不能被误判为未展示并提前释放队列展示位。
            runCatching { PushEventReporter.reportData("Notific_Show", mapOf("Notific_Type" to 1, "Notific_Position" to 1,
                "Notific_Priority" to "PRIORITY_MAX", "event_id" to "customer_general_style") +
                event.scene.source.properties() + NotificationContent(model.contentTitle, model.contentContent)
                .properties(NotificationVisibility.backgroundForDisplay())) }
                .onFailure { Logger.w("场景推送：${event.scene.logName} 展示埋点出口异常，不影响已提交的通知，异常类型=${it.javaClass.simpleName}") }
            // notify 成功只代表系统接收；是否实际弹出悬浮窗仍由系统渠道、DND、冷却和 OEM 控制。
            true
        }
    }

    internal fun model(event: SceneEvent, copy: SceneSelection, animate: Boolean = NotificationMotion.allowed(app), renderContext: Context = app): GeneralNotificationData {
        val text = SceneRichText.render(copy.text.text)
        val button = SceneRichText.plain(copy.text.button)
        val pending = entryPointPendingIntent(app, event.scene.notificationId) { intent ->
            intent.putExtra(LANDING_NOTIFICATION_ACTION, event.scene.code)
            intent.putExtra(LANDING_NOTIFICATION_FROM, "scene_push")
            intent.putExtra(NotificationTriggerSource.EXTRA, event.scene.source.value)
            // 场景只有正文。保留埋点协议字段，但不把红色片段拆成另一条标题。
            intent.putExtra(LANDING_NOTIFICATION_TITLE, "")
            intent.putExtra(LANDING_NOTIFICATION_CONTENT, text.toString())
            SceneLandingData.from(event).write(intent)
        }
        val views = SceneNotificationViews.build(renderContext, event.scene, text, button, pending, animate)
        return GeneralNotificationData(event.scene.notificationId, "", text.toString(), pending, views.first, views.second)
    }

    internal fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        for (id in listOf(CHANNEL, SILENT_CHANNEL)) {
            if (manager.getNotificationChannel(id) != null) continue // 保留用户修改的渠道行为。
            manager.createNotificationChannel(NotificationChannel(id, app.getString(R.string.scene_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                if (id == SILENT_CHANNEL) setSound(null, null)
                else setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(false)
            })
            Logger.d("场景推送：已创建${if (id == SILENT_CHANNEL) "静音循环" else "普通悬浮"}渠道")
        }
    }

    companion object {
        const val CHANNEL = "scene_smart_alerts_v1"
        const val SILENT_CHANNEL = "scene_smart_alerts_silent_v1"
    }
}
