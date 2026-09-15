package io.docview.push.timing

/** 两个接收器共享一次屏幕状态转换；不靠时间窗口，快速连续锁屏/解锁也不会被误合并。 */
internal class ScreenEventGate {
    private var screenOn: Boolean? = null
    private var unlockHandled = false

    @Synchronized
    fun accept(event: ScreenEvent, interactive: Boolean, keyguardLocked: Boolean): Boolean {
        // 缓存进程可能晚收到广播。用当前系统状态丢弃已过时的事件，
        // 防止另一接收器迟到的 OFF/ON 把已处理的解锁状态回退，造成重复推送。
        return when (event) {
            ScreenEvent.OFF -> {
                if (interactive) false else {
                    val changed = screenOn != false
                    screenOn = false
                    unlockHandled = false
                    changed
                }
            }
            ScreenEvent.ON -> {
                if (!interactive) false else {
                    val changed = screenOn != true
                    screenOn = true
                    if (keyguardLocked) unlockHandled = false
                    changed
                }
            }
            ScreenEvent.UNLOCK -> {
                if (!interactive || keyguardLocked || unlockHandled) false else {
                    screenOn = true
                    unlockHandled = true
                    true
                }
            }
        }
    }
}

internal enum class ScreenEvent { OFF, ON, UNLOCK }
internal enum class ScreenListenerOwner { APPLICATION, SERVICE }
