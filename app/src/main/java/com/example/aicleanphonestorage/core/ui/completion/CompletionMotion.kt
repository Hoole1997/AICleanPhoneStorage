package com.example.aicleanphonestorage.core.ui.completion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding

/** 单次入场；后台/失焦/省电立刻收敛到可读静态结果，不等待动画才能继续。 */
internal class CompletionMotion(private val binding: ScreenCompletionBinding, restored: Boolean) {
    var played = restored
        private set

    private var animator: ValueAnimator? = null
    internal val running: Boolean
        get() = animator != null

    fun update(resumed: Boolean, focused: Boolean, allowed: Boolean) {
        if (!resumed || !focused || !allowed) {
            stop()
            if (resumed && focused && !allowed) played = true
            return
        }
        if (played) return
        played = true
        val next =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                interpolator = LinearInterpolator()
                addUpdateListener { frame(it.animatedFraction) }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (animator === animation) {
                                animator = null
                                finishFrame()
                            }
                        }
                    }
                )
            }
        animator = next
        frame(0f)
        next.start()
    }

    private fun reveal(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse * inverse
    }

    private fun frame(progress: Float) {
        val hero = reveal(progress / 0.6f)
        binding.completionRobot.alpha = hero
        binding.completionRobot.scaleX = 0.86f + 0.14f * hero
        binding.completionRobot.scaleY = binding.completionRobot.scaleX
        binding.completionRobot.translationY =
            (1 - hero) * 18 * binding.root.resources.displayMetrics.density
        binding.completionResult.alpha = reveal((progress - 0.12f) / 0.45f)
        binding.completionBurst.frame(progress)
    }

    private fun finishFrame() = frame(1f)

    fun stop() {
        val old = animator
        animator = null
        old?.removeAllUpdateListeners()
        old?.removeAllListeners()
        old?.cancel()
        finishFrame()
    }
}
