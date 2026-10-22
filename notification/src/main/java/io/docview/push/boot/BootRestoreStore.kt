package io.docview.push.boot

import android.content.Context

/** 仅 Activity 真正启动后写入；Provider/Application 冷启动不能把“安装过”当成“打开过”。所有访问在 IO 线程。 */
internal class BootRestoreStore(context: Context) {
    private val preferences = context.getSharedPreferences("notification_boot", Context.MODE_PRIVATE)
    fun recordOpened() {
        if (!preferences.getBoolean("opened", false))
            check(preferences.edit().putBoolean("opened", true).commit())
    }
    fun wasOpened() = preferences.getBoolean("opened", false)
    fun lastAttemptBoot() = preferences.getInt("attempt_boot", -1)
    fun recordAttempt(boot: Int) {
        check(preferences.edit().putInt("attempt_boot", boot).commit())
    }
}
