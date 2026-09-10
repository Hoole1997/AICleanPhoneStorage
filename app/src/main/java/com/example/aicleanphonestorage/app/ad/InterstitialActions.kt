package com.example.aicleanphonestorage.app.ad

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** 使用用户提供的 AdExt；广告关闭/失败都只标记就绪，页面恢复后才执行当前 Activity 的业务回调。 */
internal class InterstitialActions(
    private val activity: AppCompatActivity,
    private val request: (String, (Boolean) -> Unit) -> Unit = { position, call ->
        // SDK 可从云端获取广告 ID，展示入口不按本地配置是否为空进行拦截。
        activity.loadInterstitial(
            positionName = position,
            call = call,
        )
    },
) {
    private val state = ViewModelProvider(activity)[InterstitialActionState::class.java]
    private val handlers = mutableMapOf<String, (Long) -> Unit>()
    val busy: Boolean get() = state.pending.value != null

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = drain()
            override fun onDestroy(owner: LifecycleOwner) {
                handlers.clear()
            }
        })
        activity.lifecycleScope.launch { state.pending.collect { drain() } }
    }

    fun register(action: String, handler: (Long) -> Unit) {
        handlers[action] = handler
        drain()
    }

    fun run(action: String, position: String, payload: Long = 0) {
        if (action !in handlers || activity.isFinishing || activity.isDestroyed) return
        val id = state.begin(action, payload) ?: return
        // 回调只捕获不含 UI 的 ViewModel；旧 Activity 重建后回调仍可由新 Activity 续接。
        val requestState = state
        request(position) { requestState.complete(id) }
    }

    private fun drain() {
        if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val pending = state.pending.value?.takeIf { it.ready } ?: return
        val handler = handlers[pending.action] ?: return
        state.consume() ?: return
        // 先消费再执行，SDK 重复回调或业务抛错也不会第二次执行同一清理/导航。
        handler(pending.payload)
    }
}
