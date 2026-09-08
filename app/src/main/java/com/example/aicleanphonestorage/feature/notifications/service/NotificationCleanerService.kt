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
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** 系统绑定的事件服务：不主动startService、不轮询、不保活、不申请前台服务或POST_NOTIFICATIONS。 */
class NotificationCleanerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private val connected = AtomicBoolean(false)
    private val generation = AtomicLong()
    private val selected = AtomicReference<Set<String>>(emptySet())
    private val pending = ConcurrentHashMap<String, NotificationCandidate>()
    private val rescan = AtomicBoolean(false)
    // 通知风暴只合并一次“需要处理”的信号，候选按key去重，不为每条通知创建协程。
    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun onListenerConnected() {
        super.onListenerConnected()
        session?.cancel()
        val token = generation.incrementAndGet()
        connected.set(true)
        selected.set(emptySet()); pending.clear(); rescan.set(true)
        (application as CleanApplication).container.notificationConnection.update(true)
        session = serviceScope.launch {
            coroutineScope {
                launch {
                    (application as CleanApplication).container.notificationRules.selectedPackages
                        .retryWhen { error, attempt ->
                            if (error is IOException && attempt < 2 && generation.get() == token) {
                                selected.set(emptySet()); pending.clear()
                                (application as CleanApplication).container.notificationConnection.update(false)
                                delay(250L * (attempt + 1)) // 仅两次有界重试，不常驻轮询文件。
                                true
                            } else false
                        }
                        .catch { error ->
                            if (error !is IOException) throw error
                            stopCleaning(token)
                        }
                        .collect { latest ->
                            if (generation.get() != token) return@collect
                            (application as CleanApplication).container.notificationConnection.update(true)
                            val previous = selected.getAndSet(latest)
                            if ((latest - previous).isNotEmpty()) rescan.set(true)
                            wake.trySend(Unit)
                        }
                }
                for (signal in wake) {
                    currentCoroutineContext().ensureActive()
                    if (!connected.get() || generation.get() != token) break
                    if (rescan.getAndSet(false) && selected.get().isNotEmpty()) {
                        try {
                            // 仅连接/新增规则时读取现有通知，立刻提取元数据；不缓存正文或原始数组。
                            for (notification in activeNotifications.orEmpty()) {
                                currentCoroutineContext().ensureActive()
                                candidate(notification).let { if (eligible(it)) pending.putIfAbsent(it.key, it) }
                            }
                        } catch (_: SecurityException) { stopCleaning(token); break }
                    }
                    for ((key, item) in pending) {
                        currentCoroutineContext().ensureActive()
                        if (pending.remove(key, item) && connected.get() && generation.get() == token && eligible(item)) {
                            try { cancelNotification(key) } catch (_: SecurityException) { stopCleaning(token); break }
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

    private fun stopCleaning(token: Long) {
        if (generation.get() != token) return
        connected.set(false); selected.set(emptySet()); pending.clear()
        (application as CleanApplication).container.notificationConnection.update(false)
        session?.cancel()
    }

    override fun onListenerDisconnected() {
        generation.incrementAndGet()
        (application as CleanApplication).container.notificationConnection.update(false)
        connected.set(false); selected.set(emptySet()); pending.clear(); session?.cancel()
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        generation.incrementAndGet()
        (application as CleanApplication).container.notificationConnection.update(false)
        connected.set(false); selected.set(emptySet()); pending.clear()
        serviceScope.cancel(); wake.close()
        super.onDestroy()
    }
}
