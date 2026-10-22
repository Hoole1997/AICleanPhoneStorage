package io.docview.push.scene

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class SceneRepeatPolicy(val enabled: Boolean, val count: Int)

/** 仅管理新场景的有限重复任务。主线程调用；每个 ID 一份会话，不能触及旧 TriggerCtrl 的单例任务。 */
internal class SceneRepeatLoop(
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
    private val policy: () -> SceneRepeatPolicy,
) : AutoCloseable {
    private class Session(val token: String, var job: Job? = null)
    private val sessions = mutableMapOf<Int, Session>()
    private var closed = false

    fun start(id: Int, token: String, finished: () -> Unit = {}, publish: suspend (completed: Int, current: () -> Boolean) -> Boolean) {
        cancel(id)
        if (closed) { finished(); return }
        val session = Session(token)
        sessions[id] = session
        val initial = policy()
        log("场景推送：通知ID=$id 开始本轮展示，循环开关=${if (initial.enabled) "开启" else "关闭"}，额外循环次数=${initial.count}，每轮间隔=${INTERVAL_MS}毫秒")
        session.job = scope.launch {
            try {
                var completed = 0
                while (true) {
                    delay(INTERVAL_MS)
                    fun current(): Boolean {
                        val latest = policy()
                        return !closed && sessions[id] === session && latest.enabled && completed < latest.count
                    }
                    if (!current()) { log("场景推送：通知ID=$id 本轮停止，已完成额外循环=$completed，原因=次数已用尽、开关关闭或会话结束"); break }
                    if (!publish(completed, ::current)) { log("场景推送：通知ID=$id 第${completed + 1}次重复未发布，结束本轮"); break }
                    ensureActive()
                    completed++
                    log("场景推送：通知ID=$id 第${completed}次重复已发布，等待下一个展示窗口")
                    // 最后一次重发仍保留一个 4 秒展示窗口，再交棒下一条；关闭循环时首条也保留这个窗口。
                }
            } catch (cancelled: CancellationException) { log("场景推送：通知ID=$id 循环任务已取消"); throw cancelled }
            catch (error: Exception) { log("场景推送：通知ID=$id 循环执行异常，已结束本轮，异常类型=${error.javaClass.simpleName}") }
            finally {
                if (sessions[id] === session) sessions.remove(id)
                log("场景推送：通知ID=$id 本轮展示结束，交还展示位")
                finished()
            }
        }
    }

    /** token 防止旧卡片的延迟划除回调误停同 ID 的新一轮通知。 */
    fun cancel(id: Int, token: String? = null) {
        val session = sessions[id] ?: return
        if (token == null || session.token == token) { sessions.remove(id); session.job?.cancel() }
        else log("场景推送：通知ID=$id 忽略旧轮次划除回调，当前轮继续")
    }
    fun cancelWhere(matches: (Int) -> Boolean) { sessions.keys.toList().filter(matches).forEach { cancel(it) } }
    fun cancelAll() = cancelWhere { true }
    override fun close() { closed = true; cancelAll() }
    companion object { const val INTERVAL_MS = 4_000L }
}
