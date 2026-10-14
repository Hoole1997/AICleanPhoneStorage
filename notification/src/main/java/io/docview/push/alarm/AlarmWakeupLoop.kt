package io.docview.push.alarm

/** 一次闹钟的持久化凭据；elapsedRealtime 只在相同 bootCount 内有效。 */
data class AlarmWakeupRegistration(
    val token: String,
    val bootCount: Int,
    val dueElapsedMillis: Long,
)

/** 平台存储单独注入，状态机不持有 Context，也不接触 SharedPreferences。 */
interface AlarmWakeupStore {
    var registration: AlarmWakeupRegistration?
}

/** 一个固定 PendingIntent 对应最多一个闹钟，平台实现负责查询、注册和取消。 */
interface AlarmWakeupPort {
    fun exists(): Boolean
    fun schedule(registration: AlarmWakeupRegistration)
    fun cancel()
}

/**
 * 一次性闹钟循环：处理完成后才按最新配置安排下一次，不补发系统延迟期间的次数。
 * 所有入口由平台桥接层串行调用，避免广播与远程配置更新并发修改同一份持久化凭据。
 */
internal class AlarmWakeupLoop(
    private val store: AlarmWakeupStore,
    private val port: AlarmWakeupPort,
    private val elapsedRealtime: () -> Long,
    private val bootCount: () -> Int,
    private val token: () -> String,
) {
    private var delivering = false

    fun reconcile(policy: AlarmWakeupPolicy, force: Boolean = false) {
        if (!policy.enabled) {
            cancelAndClear()
            return
        }
        // 推送尚在执行时不注册下一次，远程配置和前台初始化也不能造成重叠调度。
        if (delivering) return

        val currentBoot = bootCount()
        val current = store.registration
        if (!force && current != null && current.token.isNotEmpty() &&
            current.bootCount == currentBoot && current.dueElapsedMillis >= 0L && port.exists()
        ) {
            // 已到期的闹钟仍可能被 Doze 延后；重新进入前台不能把它不断向后推迟。
            // 同理，间隔变化从下一次注册生效，不重排正在等待的闹钟。
            return
        }

        val nextToken = token()
        require(nextToken.isNotEmpty()) { "Alarm wakeup token must not be empty" }
        val intervalMillis = policy.intervalMinutes.coerceIn(
            AlarmWakeupPolicy.MIN_INTERVAL_MINUTES,
            AlarmWakeupPolicy.MAX_INTERVAL_MINUTES,
        ) * 60_000L
        val now = elapsedRealtime().coerceAtLeast(0L)
        val due = if (now > Long.MAX_VALUE - intervalMillis) Long.MAX_VALUE else now + intervalMillis
        val next = AlarmWakeupRegistration(nextToken, currentBoot, due)
        try {
            // 先移除同一个 PendingIntent，再持久化新凭据，最后才向系统注册。
            // 即使进程在中途结束，恢复时也可通过 exists() 修复缺少的系统注册。
            port.cancel()
            store.registration = next
            port.schedule(next)
        } catch (error: Throwable) {
            cleanupAfterFailure(error)
            throw error
        }
    }

    fun beginDelivery(token: String, policy: AlarmWakeupPolicy): Boolean {
        if (!policy.enabled) {
            cancelAndClear()
            return false
        }
        if (delivering || token.isEmpty()) return false
        val current = store.registration ?: return false
        if (token != current.token || current.bootCount != bootCount() ||
            current.dueElapsedMillis < 0L || elapsedRealtime() < current.dueElapsedMillis
        ) return false

        // 一次性闹钟触发后系统可能已经消费 PendingIntent，因此这里不查询 exists()。
        // 先消费凭据再允许推送，旧广播和重复广播均不能再次触发同一轮。
        cancelAndClear()
        delivering = true
        return true
    }

    fun finishDelivery(policy: AlarmWakeupPolicy) {
        delivering = false
        reconcile(policy)
    }

    /** 取消和清除分别尝试，单一平台/存储错误不能阻止另一侧清理。 */
    private fun cancelAndClear() {
        var failure: Throwable? = null
        try {
            port.cancel()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            store.registration = null
        } catch (error: Throwable) {
            val earlier = failure
            if (earlier == null) failure = error else if (earlier !== error) earlier.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun cleanupAfterFailure(original: Throwable) {
        try {
            cancelAndClear()
        } catch (cleanup: Throwable) {
            if (cleanup !== original) original.addSuppressed(cleanup)
        }
    }
}
