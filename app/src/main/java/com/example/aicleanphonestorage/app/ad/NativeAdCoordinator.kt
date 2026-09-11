package com.example.aicleanphonestorage.app.ad

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job

/** 每个页面容器只加载一次。可见性完全交给 loadNative；GONE 的容器也必须能触发请求。 */
internal class NativeAdCoordinator(
    private val activity: FragmentActivity,
    private val container: ViewGroup,
    private val slotKey: String,
    private val lifecycleOwner: LifecycleOwner = activity,
    private val request: (String, ViewGroup, (Boolean) -> Unit) -> Job = { slot, view, complete ->
        activity.loadNative(slot, view, call = complete)
    },
) : DefaultLifecycleObserver, View.OnAttachStateChangeListener {
    private var disposed = false
    private var attempted = false
    private var completed = false
    private var loading: Job? = null
    private var generation = 0L

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        container.addOnAttachStateChangeListener(this)
        loadIfReady()
    }

    override fun onResume(owner: LifecycleOwner) = loadIfReady()

    override fun onViewAttachedToWindow(view: View) = loadIfReady()

    override fun onViewDetachedFromWindow(view: View) = Unit

    private fun loadIfReady() {
        if (
            disposed ||
                attempted ||
                activity.isFinishing ||
                activity.isDestroyed ||
                !container.isAttachedToWindow ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        attempted = true
        completed = false
        val token = ++generation
        loading = request(slotKey, container) { if (generation == token) completed = true }
    }

    override fun onStop(owner: LifecycleOwner) {
        // 未完成请求随页面退后台取消；下次恢复可重新请求，成功/失败后的普通 onResume 不重复加载。
        if (!completed && loading?.isActive == true) {
            generation++
            loading?.cancel()
            attempted = false
        }
    }

    override fun onDestroy(owner: LifecycleOwner) = dispose()

    /** 弹框关闭或 showAd 关闭时立即释放，不等宿主 Activity 销毁。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        loading?.cancel()
        loading = null
        container.removeOnAttachStateChangeListener(this)
        container.removeAllViews()
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}
