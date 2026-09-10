package com.example.aicleanphonestorage.app.ad

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider

/** 首页拥有退出广告：等首页恢复、取得焦点且绘制一帧后请求，不用固定延时或轮询。 */
internal class HomeExitAdCoordinator(
    private val activity: AppCompatActivity,
    private val root: View,
    private val ads: InterstitialActions = InterstitialActions(activity),
) {
    private val state = ViewModelProvider(activity)[HomeExitAdState::class.java]
    private var scheduled = false
    private var preDraw: OneShotPreDrawListener? = null
    private val show = Runnable {
        scheduled = false
        if (canShow()) {
            state.consume()?.let { request ->
                Log.i("CleanAds", "Home exit interstitial: source=${request.placement}")
                ads.run(EXIT, request.placement)
            }
        }
    }

    init {
        ads.register(EXIT) { schedule() }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = schedule()
            override fun onPause(owner: LifecycleOwner) = cancelFrame()
            override fun onDestroy(owner: LifecycleOwner) = cancelFrame()
        })
    }

    fun accept(intent: Intent): Boolean {
        val request = HomeExitAdContract.take(intent) ?: return false
        state.accept(request)
        schedule()
        return true
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) schedule() else cancelFrame()
    }

    private fun cancelFrame() {
        preDraw?.removeListener()
        preDraw = null
        root.removeCallbacks(show)
        scheduled = false
    }

    private fun canShow() = state.pending != null && !ads.busy &&
        !activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        activity.hasWindowFocus() && !activity.supportFragmentManager.isStateSaved &&
        activity.supportFragmentManager.fragments.none { it is DialogFragment && it.isVisible }

    private fun schedule() {
        if (scheduled || !canShow()) return
        scheduled = true
        preDraw = OneShotPreDrawListener.add(root) {
            preDraw = null
            // 下一帧再调用 SDK，确保用户先看到首页；失去焦点/进入后台后会重新等待恢复事件。
            root.postOnAnimation(show)
        }
        root.invalidate()
    }

    private companion object { const val EXIT = "ad.home.exit" }
}
