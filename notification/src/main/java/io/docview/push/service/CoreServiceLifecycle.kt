package io.docview.push.service

/** 单个 Service 实例的前台会话；由主线程调用，平台操作注入以验证失败路径。 */
internal class CoreServiceLifecycle(
    private val promote: () -> Unit,
    private val beginWork: () -> Unit,
    private val cancelWork: () -> Unit,
    private val leaveForeground: () -> Unit,
    private val stopService: () -> Unit,
    private val reportFailure: (Exception) -> Unit,
) {
    var running = false
        private set
    private var stopped = false

    /** 框架已创建 FGS 时先履行晋升契约，即便随后因禁用/停止命令立即退出，也不能省略。 */
    fun prepareForeground(): Boolean {
        if (stopped) return false
        return try { promote(); true } catch (error: Exception) { fail(error); false }
    }

    fun start(enabled: Boolean): Boolean {
        if (stopped) return false
        if (!enabled) { stop(); return false }
        return try {
            // 成功晋升后才建立运行状态。重复 start 只更新通知，不再创建一套定时任务。
            if (!prepareForeground()) return false
            if (!running) {
                running = true
                beginWork()
            }
            running
        } catch (error: Exception) {
            fail(error)
            false
        }
    }

    fun refresh(): Boolean {
        if (!running || stopped) { stop(); return false }
        return try { promote(); true } catch (error: Exception) { fail(error); false }
    }

    fun fail(error: Exception) {
        stop()
        report(error)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        running = false
        // 即使尚未晋升成功，或者某一步清理抛异常，也必须继续执行 stopSelf，避免空壳服务/重启循环。
        val failures = mutableListOf<Exception>()
        for (cleanup in listOf(cancelWork, leaveForeground, stopService)) {
            try { cleanup() } catch (error: Exception) { failures += error }
        }
        // 所有停止动作先于埋点，第三方统计的耗时也不能挤占系统给出的退出窗口。
        failures.forEach(::report)
    }

    private fun report(error: Exception) {
        // 埋点/日志异常不能破坏服务退出；不尝试吞掉 VM Error 或系统异步致命异常。
        try { reportFailure(error) } catch (_: Exception) { }
    }
}

internal enum class CoreServiceCommand {
    START, RESTORE, STOP, UPDATE, UNKNOWN;
    companion object {
        const val ACTION_START = "io.docview.push.START_KEEP_ALIVE_SERVICE"
        const val ACTION_STOP = "io.docview.push.STOP_KEEP_ALIVE_SERVICE"
        const val ACTION_UPDATE = "io.docview.push.UPDATE_FOREGROUND_NOTIFICATION"
        fun from(intentIsNull: Boolean, action: String?): CoreServiceCommand = when {
            intentIsNull -> RESTORE
            action == ACTION_START -> START
            action == ACTION_STOP -> STOP
            action == ACTION_UPDATE -> UPDATE
            else -> UNKNOWN
        }
    }
}
