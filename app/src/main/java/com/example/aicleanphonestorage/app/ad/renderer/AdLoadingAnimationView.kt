package com.example.aicleanphonestorage.app.ad.renderer

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.example.aicleanphonestorage.core.ui.motion.MotionPreferences

/** Lottie 只在弹框可见且宿主恢复时播放；不让 SDK 的全局 renderer 持有 Activity 或动画 View。 */
class AdLoadingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LottieAnimationView(context, attrs) {
    // 父类构造时可能触发 visibility 回调，须等本类字段完成初始化。
    private var initialized = false
    private var released = false
    private var active = false
    private var motionAllowed = false
    private var playbackRequested = false
    private var ownerLifecycle: Lifecycle? = null
    private val preferences = MotionPreferences(context) { allowed ->
        motionAllowed = allowed
        updatePlayback()
    }
    private val observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) = updateActivity()
        override fun onPause(owner: LifecycleOwner) = updateActivity()
        override fun onDestroy(owner: LifecycleOwner) = release()
    }

    init { initialized = true }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        released = false
        // XPopup 的独立窗口未必设置 ViewTreeLifecycleOwner，回退到 Context 中的 Activity。
        ownerLifecycle = (findViewTreeLifecycleOwner() ?: contextLifecycleOwner())?.lifecycle
        ownerLifecycle?.addObserver(observer)
        updateActivity()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateActivity()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateActivity()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateActivity()
    }

    private fun updateActivity() {
        if (!initialized) return
        val next = !released && isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
            hasWindowFocus() && ownerLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false
        if (next != active) {
            active = next
            if (next) preferences.start() else preferences.stop()
        }
        updatePlayback()
    }

    private fun updatePlayback() {
        if (!initialized) return
        if (active && motionAllowed) {
            // JSON 异步解析完成前 isAnimating 仍为 false，避免排入多个待播放任务。
            if (!playbackRequested) {
                playbackRequested = true
                resumeAnimation()
            }
        } else {
            playbackRequested = false
            pauseAnimation()
            // 省电、触控探索或关闭系统动画时保留静态图形，不隐藏加载状态。
            if (active && !motionAllowed) progress = 0f
        }
    }

    fun release() {
        if (!initialized) return
        released = true
        active = false
        playbackRequested = false
        ownerLifecycle?.removeObserver(observer)
        ownerLifecycle = null
        preferences.stop()
        cancelAnimation()
    }

    private fun contextLifecycleOwner(): LifecycleOwner? {
        var candidate = context
        while (candidate is ContextWrapper) {
            if (candidate is LifecycleOwner) return candidate
            val base = candidate.baseContext
            if (base === candidate) break
            candidate = base
        }
        return candidate as? LifecycleOwner
    }
}
