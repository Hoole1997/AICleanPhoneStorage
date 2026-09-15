package com.example.aicleanphonestorage.feature.startup

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import com.example.aicleanphonestorage.app.ad.loadSplash
import com.example.aicleanphonestorage.app.ad.HotStartAdLog
import kotlinx.coroutines.launch

/** 等启动页可见、语言准备完成后调用用户提供的 loadSplash；SDK 管理加载、展示和失败规则。 */
internal class StartupAdCoordinator(
    private val activity: AppCompatActivity,
    private val root: View,
    private val model: StartupViewModel,
    private val request: (String, (Boolean) -> Unit, (Boolean) -> Unit) -> (() -> Unit) = { position, loaded, call ->
        val job = activity.loadSplash(positionName = position, onLoaded = loaded, call = call)
        val cancel: () -> Unit = {
            job.cancel()
            // 仅在本次启动广告尚未加载完成的离线退出路径调用，不关闭已经展示的广告。
            com.android.common.bill.ui.dialog.ADLoadingDialog.hide()
        }
        cancel
    },
    private val network: Flow<StartupNetworkState> = StartupNetworkMonitor(activity).states,
) {
    private var scheduled = false
    private var preDraw: OneShotPreDrawListener? = null
    private var abortRequest: (() -> Unit)? = null
    private val show = Runnable {
        scheduled = false
        if (canShow()) {
            val id = model.beginAd() ?: return@Runnable
            // 回调只捕获 ViewModel 和请求编号，旋转后的新 Activity 可续接，彻底退出后忽略迟到回调。
            val state = model
            val hot = state.entry().hotStart
            if (hot) HotStartAdLog.event("splash_request slot=$PLACEMENT requestId=$id")
            abortRequest = request(PLACEMENT, { loaded ->
                // 超时状态发布与协程取消之间也可能收到 SDK 回调，展示前再次检查本次请求资格。
                if (!state.isAdRequestActive(id)) throw kotlinx.coroutines.CancellationException("Startup request ended")
                if (loaded) state.adContentLoaded(id)
            }) { success ->
                if (hot) HotStartAdLog.event("splash_callback slot=$PLACEMENT requestId=$id success=$success")
                state.adFinished(id)
            }
        }
    }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = schedule()
            override fun onPause(owner: LifecycleOwner) = cancelFrame()
            override fun onDestroy(owner: LifecycleOwner) { cancelFrame(); abortRequest = null }
        })
        activity.lifecycleScope.launch {
            model.state.collect { state ->
                state.offlineTimeout?.let { id ->
                    cancelFrame()
                    try { abortRequest?.invoke() }
                    catch (error: Exception) { android.util.Log.w("CleanAds", "Startup offline cancellation failed", error) }
                    finally {
                        abortRequest = null
                        model.offlineWaitReleased(id)
                    }
                }
                schedule()
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.visibilityChanged(true)
                try { network.collect(model::networkChanged) }
                finally { model.visibilityChanged(false) }
            }
        }
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
