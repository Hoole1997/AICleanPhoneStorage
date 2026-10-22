package io.docview.push.scene

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 新场景专用 FIFO 展示位。主线程调用；每场景最多一个候选，只存元数据对应的等待者。 */
internal class ScenePresentationQueue(private val log: (String) -> Unit = {}) : AutoCloseable {
    private class Waiter(val scene: PushScene, val ready: CompletableDeferred<Lease?> = CompletableDeferred())
    class Lease internal constructor(private val current: () -> Boolean, private val finish: () -> Unit) {
        val isCurrent get() = current()
        fun release() = finish()
    }
    private val waiting = linkedMapOf<PushScene, Waiter>()
    private var active: Pair<Waiter, Lease>? = null
    private var closed = false

    suspend fun acquire(scene: PushScene): Lease? {
        if (closed) { log("场景推送：${scene.logName} 未入队，队列已关闭"); return null }
        val waiter = Waiter(scene)
        // 同场景合并最新候选并保留队列位置；不同场景严格按进入展示队列的顺序处理。
        waiting.put(scene, waiter)?.let { log("场景推送：${scene.logName} 合并为最新待播事件"); it.ready.complete(null) }
        log("场景推送：${scene.logName} 触发事件入队，待播数量=${waiting.size}，当前播放=${active?.first?.scene?.logName ?: "无"}")
        advance()
        var delivered = false
        try {
            val lease = waiter.ready.await()
            currentCoroutineContext().ensureActive()
            if (lease?.isCurrent != true) return null // 已晋升但尚未恢复时回前台，不能把旧候选重新排队。
            delivered = true
            return lease
        } finally {
            if (!delivered) {
                if (waiting[scene] === waiter) { waiting.remove(scene); log("场景推送：${scene.logName} 取消排队，剩余待播数量=${waiting.size}") }
                waiter.ready.cancel()
                if (active?.first === waiter) active?.second?.release()
            }
        }
    }

    private fun advance() {
        if (closed || active != null) return
        val waiter = waiting.values.firstOrNull() ?: return
        waiting.remove(waiter.scene)
        lateinit var lease: Lease
        lease = Lease(current = { !closed && active?.second === lease }, finish = {
            if (active?.second === lease) {
                log("场景推送：${waiter.scene.logName} 释放展示位，剩余待播数量=${waiting.size}")
                active = null; advance()
            }
        })
        active = waiter to lease
        log("场景推送：${waiter.scene.logName} 触发事件出队，开始复核展示条件，剩余待播数量=${waiting.size}")
        waiter.ready.complete(lease)
    }

    /** 回前台清空候选，不缓存到下次退后台；旧会话迟到的 release 不能释放新的展示位。 */
    fun clear() {
        val cancelled = waiting.values.toList()
        if (active != null || cancelled.isNotEmpty()) log("场景推送：清空播放队列，当前播放=${active?.first?.scene?.logName ?: "无"}，取消待播数量=${cancelled.size}")
        waiting.clear(); active = null
        cancelled.forEach { it.ready.complete(null) }
    }
    override fun close() { closed = true; clear() }
}
