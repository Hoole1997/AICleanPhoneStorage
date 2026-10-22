package io.docview.push.boot

/** 开机恢复只做准入判断；启动失败不改变为其他后台拉起方式。 */
internal object BootRestorePolicy {
    fun skipReason(opened: Boolean, enabled: Boolean, permission: Boolean, restricted: Boolean,
                   running: Boolean, boot: Int, lastAttempt: Int): String? = when {
        !opened -> "尚未打开过应用"
        !enabled -> "前台服务开关关闭"
        !permission -> "通知权限未开启"
        restricted -> "系统限制后台运行"
        running -> "前台服务已经运行"
        boot < 0 -> "无法确认本次开机编号"
        boot == lastAttempt -> "本次开机已经尝试恢复"
        else -> null
    }
}

/** 广播失败后，初始化刷新/屏幕回调不能变成隐式重试；下次真正进入前台解除。 */
internal object BootRestoreSession {
    val attempts = kotlinx.coroutines.sync.Mutex()
    @Volatile private var waitingForForeground = false
    fun begin() { waitingForForeground = true }
    fun allowNormalStart(background: Boolean): Boolean {
        if (!background) waitingForForeground = false
        return !waitingForForeground
    }
}
