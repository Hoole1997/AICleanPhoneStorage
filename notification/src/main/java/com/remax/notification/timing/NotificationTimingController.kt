package com.remax.notification.timing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.remax.notification.NotificationRuntime

/** 保留原模块前后台/解锁时机。动态系统事件接收器随进程消亡，不申请后台存活。 */
class NotificationTimingController(context: Context, private val runtime: NotificationRuntime) : DefaultLifecycleObserver {
    private val app = context.applicationContext
    private var installed = false
    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) runtime.onUnlock()
        }
    }

    fun initialize() {
        if (installed) return
        installed = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        ContextCompat.registerReceiver(app, unlock, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStart(owner: LifecycleOwner) = runtime.onForeground()
    override fun onStop(owner: LifecycleOwner) = runtime.onBackground()
}
