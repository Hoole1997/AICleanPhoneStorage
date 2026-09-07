package com.example.aicleanphonestorage.feature.notifications.service

import android.app.Notification
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.aicleanphonestorage.app.CleanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 系统绑定的事件服务：不主动startService、不轮询、不保活、不申请前台服务或POST_NOTIFICATIONS。 */
class NotificationCleanerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private val connected = AtomicBoolean(false)
    private val selected = AtomicReference<Set<String>>(emptySet())
    private val pending = ConcurrentHashMap<String, NotificationCandidate>()
    private val rescan = AtomicBoolean(false)
    // 通知风暴只合并一次“需要处理”的信号，候选按key去重，不为每条通知创建协程。
    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun onListenerConnected() {
        super.onListenerConnected()
        session?.cancel()
        connected.set(true)
        selected.set(emptySet()); pending.clear(); rescan.set(true)
        (application as CleanApplication).container.notificationConnection.update(true)
        session = serviceScope.launch {
            coroutineScope {
                launch {
                    (application as CleanApplication).container.notificationRules.selectedPackages
                        .catch { error ->
                            (application as CleanApplication).container.notificationConnection.update(false)
                            selected.set(emptySet()) // 规则读取失败时停止清理，不以旧选择继续删除。
                            if (error !is IOException) throw error
                        }
                        .collect { latest ->
                            val previous = selected.getAndSet(latest)
                            if ((latest - previous).isNotEmpty()) rescan.set(true)
                            wake.trySend(Unit)
                        }
                }
                for (signal in wake) {
                    currentCoroutineContext().ensureActive()
                    if (!connected.get()) break
                    if (rescan.getAndSet(false) && selected.get().isNotEmpty()) {
                        try {
                            // 仅连接/新增规则时读取现有通知，立刻提取元数据；不缓存正文或原始数组。
                            for (notification in activeNotifications.orEmpty()) {
                                currentCoroutineContext().ensureActive()
                                candidate(notification).let { if (eligible(it)) pending.putIfAbsent(it.key, it) }
                            }
                        } catch (_: SecurityException) { selected.set(emptySet()); break }
                    }
                    for ((key, item) in pending) {
                        currentCoroutineContext().ensureActive()
                        if (pending.remove(key, item) && connected.get() && eligible(item)) {
                            try { cancelNotification(key) } catch (_: SecurityException) { selected.set(emptySet()); break }
                        }
                    }
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // API24+回调在主线程，只读包名/key/标志位并入队；Binder取消与规则I/O在后台完成。
        val item = candidate(sbn)
        if (connected.get() && eligible(item)) { pending[item.key] = item; wake.trySend(Unit) }
        else pending.remove(item.key)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { pending.remove(sbn.key) }
    private fun eligible(item: NotificationCandidate) = NotificationClearPolicy.shouldClear(item, selected.get(), packageName)
    private fun candidate(sbn: StatusBarNotification) = NotificationCandidate(sbn.key, sbn.packageName,
        sbn.user == Process.myUserHandle(), sbn.isClearable, sbn.isOngoing,
        sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)

    override fun onListenerDisconnected() {
        (application as CleanApplication).container.notificationConnection.update(false)
        connected.set(false); selected.set(emptySet()); pending.clear(); session?.cancel()
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        (application as CleanApplication).container.notificationConnection.update(false)
        connected.set(false); selected.set(emptySet()); pending.clear()
        serviceScope.cancel(); wake.close()
        super.onDestroy()
    }
}
