package com.example.aicleanphonestorage.core.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 进程初始化、回前台、安装/卸载事件驱动刷新；无计时器、轮询或新权限请求。 */
internal class InstalledAppCountMonitor(
    context: Context,
    private val repository: InstalledAppCountRepository,
    private val onCountChanged: () -> Unit,
) : DefaultLifecycleObserver {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 应用升级的临时移除不发布数量波动，随后 ADDED/REPLACED 会给出最终状态。
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            requests.trySend(Unit)
        }
    }

    @androidx.annotation.MainThread
    fun start() {
        if (started) return
        started = true
        // 先订阅再请求，避免首次读取很快完成而漏掉通知刷新；只跳过初始未知值。
        scope.launch { repository.count.drop(1).collect { onCountChanged() } }
        scope.launch {
            for (request in requests) {
                try { repository.refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("InstalledAppCount", "Unable to refresh application count", error) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // 只接收受保护的系统安装包事件；不在 Application 中持有 Activity 或 Service。
        ContextCompat.registerReceiver(app, packages, filter, ContextCompat.RECEIVER_EXPORTED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        requests.trySend(Unit)
    }

    override fun onStart(owner: LifecycleOwner) { requests.trySend(Unit) }
}
