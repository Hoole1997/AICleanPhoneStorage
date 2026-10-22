package io.docview.push.scene

import kotlinx.coroutines.*

internal interface SceneHistoryStore {
    suspend fun history(scene: PushScene): SceneHistory
    suspend fun succeeded(scene: PushScene, moment: SceneMoment, copyId: String)
}

/** 每个方式一个延迟任务与独立历史，不竞争优先级、不读写旧推送间隔、重复任务或每日额度。主线程调用。 */
internal class SceneDispatcher(
    private val scope: CoroutineScope,
    private val configuration: () -> SceneConfiguration,
    private val history: SceneHistoryStore,
    private val clock: () -> SceneMoment,
    private val language: () -> String,
    private val background: () -> Boolean,
    private val validate: suspend (SceneEvent) -> Boolean,
    private val publish: suspend (SceneEvent, SceneSelection, ScenePresentationQueue.Lease?, () -> Boolean) -> Boolean,
    private val foregroundHint: (SceneEvent, SceneSelection) -> Unit = { _, _ -> },
    private val refreshFacts: (SceneEvent) -> SceneEvent = { it },
    private val presentation: ScenePresentationQueue? = null,
    private val log: (String) -> Unit = {},
    private val failed: (PushScene, Exception) -> Unit = { _, _ -> },
) : AutoCloseable {
    private data class Pending(val token: Long, val event: SceneEvent, var job: Job? = null, var waitingForSlot: Boolean = false)
    private val pending = mutableMapOf<PushScene, Pending>()
    private val successful = mutableMapOf<PushScene, SceneHistory>()
    private var sequence = 0L
    private var closed = false

    fun offer(event: SceneEvent) {
        if (closed) { log("场景推送：${event.scene.logName} 不再调度，服务会话已关闭"); return }
        cancel(event.scene)
        if (!configuration().enabled(event.scene)) { log("场景推送：${event.scene.logName} 跳过，远程或用户开关已关闭"); return }
        val item = Pending(++sequence, event)
        pending[event.scene] = item
        item.job = scope.launch {
            try {
                val waitMs = (event.dueElapsed - clock().elapsed).coerceAtLeast(0)
                log("场景推送：${event.scene.logName} 调度触发候选，事件序号=${item.token}，触发确认等待=${waitMs}毫秒")
                delay(waitMs)
                while (isActive && eligible(item)) {
                    val selectedConfig = configuration()
                    val persisted = successful[event.scene] ?: history.history(event.scene)
                    val saved = successful[event.scene] ?: persisted
                    if (configuration() != selectedConfig) continue
                    val configured = selectedConfig.policy.rules.getValue(event.scene).intervalMs
                    val interval = if (event.scene == PushScene.CHARGE_START) configured.coerceAtLeast(300_000L) else configured
                    val remaining = saved.remaining(clock(), interval)
                    if (remaining > 0) { log("场景推送：${event.scene.logName} 自身间隔未到，剩余=${remaining}毫秒"); delay(remaining); continue }
                    item.waitingForSlot = true
                    val lease = presentation?.acquire(event.scene)
                    if (presentation != null && lease == null) { log("场景推送：${event.scene.logName} 排队已取消，本次未展示"); return@launch }
                    var transferred = false
                    try {
                        // 出队后重新取配置、频控、事实和文案；入队时不能提前 notify、记次数或锁定旧文案。
                        while (isActive && eligible(item) && lease?.isCurrent != false) {
                            val currentConfig = configuration()
                            val currentHistory = successful[event.scene] ?: history.history(event.scene)
                            if (configuration() != currentConfig) continue
                            val currentInterval = currentConfig.policy.rules.getValue(event.scene).intervalMs.let {
                                if (event.scene == PushScene.CHARGE_START) it.coerceAtLeast(300_000L) else it
                            }
                            // 等待期间远程延长了间隔：释放展示位，回到自己的冷却，不阻塞后面的场景。
                            if (currentHistory.remaining(clock(), currentInterval) > 0) {
                                log("场景推送：${event.scene.logName} 出队后间隔配置已变化，释放展示位重新等待")
                                break
                            }
                            if (!validate(event)) { log("场景推送：${event.scene.logName} 不再满足原触发条件，跳过本次展示"); return@launch }
                            ensureActive()
                            if (!eligible(item) || lease?.isCurrent == false) return@launch
                            if (configuration() != currentConfig) continue
                            val fresh = refreshFacts(event)
                            val copy = SceneCopies.select(currentConfig.copies[event.scene].orEmpty(), currentHistory.copyId, fresh, language())
                                ?: run { log("场景推送：${event.scene.logName} 没有匹配当前事实的有效文案，本次不展示"); return@launch }
                            if (!background()) { log("场景推送：${event.scene.logName} 应用在前台，仅发送页面提示"); foregroundHint(fresh, copy); return@launch }
                            val sent = publish(fresh, copy, lease) {
                                eligible(item) && lease?.isCurrent != false && background() && configuration() == currentConfig
                            }
                            if (!sent && configuration() != currentConfig && eligible(item)) {
                                log("场景推送：${event.scene.logName} 构建期间配置发生变化，重新校验")
                                continue
                            }
                            if (sent) {
                                transferred = true // 成功后展示位归循环持有，最后一轮展示结束/取消才释放。
                                val moment = clock()
                                successful[event.scene] = SceneHistory(moment.wall, moment.elapsed, moment.boot, copy.id)
                                withContext(NonCancellable) { history.succeeded(event.scene, moment, copy.id) }
                                log("场景推送：${event.scene.logName} 首次发布成功，已记录本场景间隔并推进文案轮播")
                            } else {
                                log("场景推送：${event.scene.logName} 本次未发布，不记录次数、不推进文案轮播")
                            }
                            return@launch
                        }
                    } finally {
                        item.waitingForSlot = false
                        if (!transferred) lease?.release()
                    }
                }
            } catch (cancelled: CancellationException) { log("场景推送：${event.scene.logName} 待处理任务已取消，事件序号=${item.token}"); throw cancelled }
            catch (error: Exception) { failed(event.scene, error) }
            finally { if (pending[event.scene]?.token == item.token) pending.remove(event.scene) }
        }
    }

    private fun eligible(item: Pending) = !closed && pending[item.event.scene]?.token == item.token && configuration().enabled(item.event.scene)
    fun cancel(scene: PushScene) { pending.remove(scene)?.job?.cancel() }
    fun reconsider() {
        pending.values.toList().forEach {
            if (!configuration().enabled(it.event.scene)) cancel(it.event.scene)
            else if (!it.waitingForSlot) offer(it.event)
            // 已排队的有效候选保留原位置，出队复核会使用最新配置。
        }
    }
    override fun close() {
        log("场景推送：关闭触发调度，取消待处理任务数量=${pending.size}")
        closed = true
        val jobs = pending.values.mapNotNull { it.job }
        pending.clear()
        jobs.forEach(Job::cancel)
    }
}
