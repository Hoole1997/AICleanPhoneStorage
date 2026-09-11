package com.example.aicleanphonestorage.feature.startup

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.app.ad.loadSplash
import kotlinx.coroutines.launch

/** 等启动页可见、语言准备完成后调用用户提供的 loadSplash；SDK 管理加载、展示和失败规则。 */
internal class StartupAdCoordinator(
    private val activity: AppCompatActivity,
    private val root: View,
    private val model: StartupViewModel,
    private val request: (String, (Boolean) -> Unit) -> Unit = { position, call ->
        activity.loadSplash(positionName = position, call = call)
    },
) {
    private var scheduled = false
    private var preDraw: OneShotPreDrawListener? = null
    private val show = Runnable {
        scheduled = false
        if (canShow()) {
            val id = model.beginAd() ?: return@Runnable
            // 回调只捕获 ViewModel 和请求编号，旋转后的新 Activity 可续接，彻底退出后忽略迟到回调。
            val state = model
            request(PLACEMENT) { state.adFinished(id) }
        }
    }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = schedule()
            override fun onPause(owner: LifecycleOwner) = cancelFrame()
            override fun onDestroy(owner: LifecycleOwner) = cancelFrame()
        })
        activity.lifecycleScope.launch { model.state.collect { schedule() } }
    }

    fun windowFocusChanged(focused: Boolean) {
        if (focused) schedule() else cancelFrame()
    }

    private fun canShow() = model.canRequestAd() && !activity.isFinishing && !activity.isDestroyed &&
        root.isAttachedToWindow && activity.hasWindowFocus() &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun schedule() {
        if (scheduled || !canShow()) return
        scheduled = true
        preDraw = OneShotPreDrawListener.add(root) {
            preDraw = null
            root.postOnAnimation(show)
        }
        root.invalidate()
    }

    private fun cancelFrame() {
        preDraw?.removeListener()
        preDraw = null
        root.removeCallbacks(show)
        scheduled = false
    }

    private companion object { const val PLACEMENT = "splash" }
}
