package com.example.aicleanphonestorage.feature.rating

import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 首页仅提供资格判断；持久化、弹层和 Play API 分工，窗口恢复后再执行，禁止后台拉起或叠层。 */
internal class RatingPromptCoordinator(
    private val activity: AppCompatActivity,
    private val root: View,
    private val model: RatingPromptViewModel,
    private val ready: () -> Boolean,
    private val review: PlayReviewLauncher = GooglePlayReviewLauncher(),
    private val blocked: () -> Boolean = { ForegroundTransitionGuard.blockedExcept("in_app_review") },
) : DefaultLifecycleObserver {
    private var preDraw: OneShotPreDrawListener? = null
    private var scheduled = false
    private var reviewJob: Job? = null
    private var reviewTransition: AutoCloseable? = null
    private val manager get() = activity.supportFragmentManager
    private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) drain()
        }
    }
    private val show = Runnable {
        scheduled = false
        if (!available()) return@Runnable
        when (model.phase.value) {
            RatingPhase.WAITING -> model.claim()
            RatingPhase.READY -> {
                Log.d("RatingPrompt", "Show custom rating prompt")
                RatingPromptDialog().showNow(manager, RatingPromptDialog.TAG)
            }
            RatingPhase.REVIEW_PENDING -> launchReview()
            else -> Unit
        }
    }

    init {
        manager.setFragmentResultListener(RatingPromptDialog.SHOWN, activity) { _, _ -> model.shown() }
        manager.setFragmentResultListener(RatingPromptDialog.RESULT, activity) { _, result ->
            model.decide(result.getInt(RatingPromptDialog.STARS), result.getBoolean(RatingPromptDialog.SUBMIT))
            reserveReviewTransition()
            drain()
        }
        reserveReviewTransition()
        manager.registerFragmentLifecycleCallbacks(fragments, false)
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch { model.phase.collect { drain() } }
        activity.lifecycleScope.launch { ForegroundTransitionGuard.changes.collect { drain() } }
    }

    fun drain() {
        if (scheduled || !available() || model.phase.value !in setOf(RatingPhase.WAITING, RatingPhase.READY, RatingPhase.REVIEW_PENDING)) return
        scheduled = true
        preDraw = OneShotPreDrawListener.add(root) {
            preDraw = null
            root.postOnAnimation(show)
        }
        root.invalidate()
    }

    private fun available(): Boolean =
        ready() && !blocked() && !activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus() &&
            !manager.isStateSaved && manager.fragments.none { it is DialogFragment && it.isAdded }

    private fun reserveReviewTransition() {
        if (model.phase.value == RatingPhase.REVIEW_PENDING && reviewTransition == null)
            // 在自定义弹框关闭之前就保留原生评价交接，防止恢复焦点时先插入退出广告。
            reviewTransition = ForegroundTransitionGuard.hold("in_app_review")
    }

    private fun launchReview() {
        if (!model.beginReview()) return
        reviewJob = activity.lifecycleScope.launch {
            val transition = reviewTransition ?: ForegroundTransitionGuard.hold("in_app_review")
            reviewTransition = transition
            try {
                Log.d("RatingPrompt", "Request Google Play in-app review")
                review.launch(activity)
                Log.d("RatingPrompt", "Play flow completed; visibility/submission are unknown")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // Play 缺失、配额或请求错误不阻断首页，不显示误导性的成功提示，也不另跳商店。
                Log.w("RatingPrompt", "Play review unavailable", error)
            } finally {
                model.finish()
                transition.close()
                reviewTransition = null
            }
        }
    }

    fun windowFocusChanged(focused: Boolean) { if (focused) drain() else cancelFrame() }
    override fun onResume(owner: LifecycleOwner) = drain()
    override fun onPause(owner: LifecycleOwner) = cancelFrame()
    override fun onDestroy(owner: LifecycleOwner) {
        cancelFrame()
        manager.unregisterFragmentLifecycleCallbacks(fragments)
        reviewJob?.cancel()
        reviewTransition?.close(); reviewTransition = null
        if (model.phase.value == RatingPhase.REVIEW_RUNNING) model.finish()
    }
    private fun cancelFrame() {
        preDraw?.removeListener(); preDraw = null
        root.removeCallbacks(show)
        scheduled = false
    }
}
