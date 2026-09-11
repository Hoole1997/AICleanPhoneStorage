package com.example.aicleanphonestorage.app.ad

/** 仅保存进程内状态：冷启动不触发，每次真实前后台切换最多消费一次资格。 */
internal class HotStartState {
    private var foregroundSeen = false
    private var returning = false
    private var pending = false

    fun background(eligibleDeparture: Boolean) {
        returning = foregroundSeen && eligibleDeparture
        pending = false
    }

    fun foreground() {
        pending = returning
        returning = false
        foregroundSeen = true
    }

    fun description() = "foregroundSeen=$foregroundSeen returning=$returning pending=$pending"

    fun take(): Boolean = pending.also { pending = false }
}
