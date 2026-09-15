package com.example.aicleanphonestorage.core.analytics

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.report.ReportDataManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 事件只携带小型数值、白名单枚举或有界自家通知文案；SDK 初始化前有界缓存，顺序上报，不阻塞业务主线程。 */
internal object BusinessTelemetry : EventSink {
    private data class Pending(val event: MetricEvent, val parameters: Map<String, Any>, val permission: Deferred<String>? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val dropped = AtomicInteger()
    private val events = Channel<Pending>(256, BufferOverflow.DROP_OLDEST, onUndeliveredElement = { it.permission?.cancel(); dropped.incrementAndGet() })
    private val started = AtomicBoolean()

    override fun send(event: MetricEvent, parameters: Map<String, Any>) = emit(event, parameters)

    fun emit(event: MetricEvent, parameters: Map<String, Any> = emptyMap()) {
        events.trySend(Pending(event, parameters.toMap()))
    }

    fun withPermission(event: MetricEvent, parameters: Map<String, Any>, permission: suspend () -> String) {
        // 入队顺序先固定；权限立即异步采集，不等 SDK 就绪后再读取已可能改变的授权状态。
        val captured = scope.async { permission() }
        events.trySend(Pending(event, parameters.toMap(), captured))
    }

    /** 由 metrics 初始化完成后调用，只启动一个消费者，避免冷启动早期事件因 SDK 未就绪丢失。 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (pending in events) try {
                val params = pending.parameters.toMutableMap()
                pending.permission?.let { params["perm_granted"] = it.await() }
                if (pending.event == MetricEvent.APP_LAUNCH || pending.event == MetricEvent.NOTIFBAR_ENTRY_CLICK)
                    params["user_type"] = userType(ChannelUserController.getCurrentChannel())
                if (ReportDataManager.isInitialized()) {
                    ReportDataManager.reportData(pending.event.wireName, params)
                    Log.d("BusinessMetrics", "${pending.event.wireName} $params")
                } else Log.w("BusinessMetrics", "No configured reporter: ${pending.event.wireName}")
                val lost = dropped.getAndSet(0)
                if (lost > 0) Log.w("BusinessMetrics", "Startup queue overflow: dropped=$lost")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.w("BusinessMetrics", "Event reporting failed: ${pending.event.wireName}", error) }
        }
    }

    fun userType(channel: ChannelUserController.UserChannelType) = when (channel) {
        ChannelUserController.UserChannelType.PAID -> "paid"
        ChannelUserController.UserChannelType.NATURAL -> "organic"
    }
}
