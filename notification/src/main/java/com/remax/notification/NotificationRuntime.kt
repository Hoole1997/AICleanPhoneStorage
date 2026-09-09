package com.remax.notification

import android.content.Context
import android.util.Log
import com.remax.notification.check.NotificationCheckController
import com.remax.notification.check.NotificationType
import com.remax.notification.config.NotificationConfigController
import com.remax.notification.controller.NotificationTriggerController
import com.remax.notification.utils.FCMTopicManager
import java.time.ZonedDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用级仅持有 Application、少量配置与最多 8 个事件。等待 Channel 时不采样、不唤醒设备。
 * 所有本地 I/O 与发布串行离开主线程；FCM 回调等待同一短事务，避免服务结束后丢失消息。
 */
class NotificationRuntime(context: Context, private val host: NotificationHost) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<NotificationType?>(8, BufferOverflow.DROP_OLDEST)
    private val transaction = Mutex()
    private val publisher = NotificationTriggerController(app, host)
    private val config = NotificationConfigController(app)
    private val preferences by lazy { app.getSharedPreferences("push_delivery", Context.MODE_PRIVATE) }
    private val installedAt by lazy {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
    }
    private val topics = FCMTopicManager(app)
    @Volatile private var foreground = false

    init {
        scope.launch {
            // 先冻结本次策略，再异步请求下次启动的策略；不等待网络。
            config.config
            config.fetchForNextStart()
            for (event in events) {
                try {
                    transaction.withLock {
                        if (event == null) {
                            publisher.resident()
                            topics.subscribeCommonTopic()
                        } else deliver(event, host.defaultContent())
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    // 不记录 token 或消息正文；失败不产生补偿轮询。
                    Log.w("CleanPush", "Notification event failed: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    fun onForeground() { foreground = true; refreshResident() }
    fun onBackground() { foreground = false; events.trySend(NotificationType.BACKGROUND) }
    fun onUnlock() { events.trySend(NotificationType.UNLOCK) }
    fun refreshResident() { events.trySend(null) }

    /** 每个角标都由宿主更新后主动刷新；不为了角标触发扫描或维护后台订阅。 */
    suspend fun receive(data: Map<String, String>, messageId: String?) = withContext(Dispatchers.IO) {
        transaction.withLock {
            if (!NotificationCheckController.versionMatches(data["version"], host.versionName)) return@withLock
            val fallback = host.defaultContent()
            deliver(NotificationType.FCM, PushMessage(
                title = data["title"]?.takeIf { it.isNotBlank() } ?: fallback.title,
                body = (data["body"] ?: data["desc"])?.takeIf { it.isNotBlank() } ?: fallback.body,
                destination = NotificationDestination.fromKey(data["destination"]),
                messageId = messageId?.take(200),
            ))
        }
    }

    fun onTokenChanged() { refreshResident() }

    private fun deliver(type: NotificationType, message: PushMessage) {
        val now = ZonedDateTime.now()
        val day = now.toLocalDate().toString()
        val count = if (preferences.getString("day", null) == day) preferences.getInt("count", 0) else 0
        val recentIds = preferences.getString("recent_ids", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        if (message.messageId != null && message.messageId in recentIds) return
        if (!NotificationCheckController.allowed(config.config, type, now, installedAt,
                foreground, count, preferences.getLong("last_${type.name}", 0))) return
        if (!publisher.push(message)) return
        // 按实际发布日期惰性换日，不使用午夜定时器；只有发布成功才消耗额度。
        preferences.edit().putString("day", day).putInt("count", count + 1)
            .putLong("last_${type.name}", now.toInstant().toEpochMilli())
            .putString("recent_ids", (recentIds + listOfNotNull(message.messageId)).takeLast(32).joinToString("\n"))
            .commit()
    }
}
