package com.example.aicleanphonestorage.core.lifecycle

/** 主线程使用。只记录交接数量，不持有 Activity；系统授权/全屏广告返回不是新的主动启动。 */
internal object ForegroundTransitionGuard {
    private var holders = 0
    private val reasons = mutableMapOf<String, Int>()
    fun description(): String = reasons.entries.joinToString { "${it.key}:${it.value}" }.ifEmpty { "none" }
    val blocked: Boolean get() = holders > 0

    fun hold(reason: String = "external_ui"): AutoCloseable {
        holders++
        reasons[reason] = (reasons[reason] ?: 0) + 1
        var closed = false
        return AutoCloseable {
            if (!closed) {
                closed = true
                holders--
                val remaining = (reasons[reason] ?: 1) - 1
                if (remaining == 0) reasons.remove(reason) else reasons[reason] = remaining
            }
        }
    }
}
