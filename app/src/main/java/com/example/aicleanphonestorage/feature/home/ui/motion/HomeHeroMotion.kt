package com.example.aicleanphonestorage.feature.home.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.aicleanphonestorage.databinding.ItemHomeHeroBinding
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 一个 ViewHolder 共用一个循环时间轴；可见且允许动效时连续播放，失活立即取消并还原。 */
internal class HomeHeroMotion(private val binding: ItemHomeHeroBinding) {
    private val visible = Rect()
    private val lift = 3f * binding.root.resources.displayMetrics.density
    private var active = false
    private var animator: ValueAnimator? = null

    fun setActive(value: Boolean) {
        active = value
        if (value) refreshVisibility() else stop()
    }

    fun refreshVisibility() {
        if (!active) return
        if (!visibleEnough(binding.robotArt) && !visibleEnough(binding.cleanButton)) stop()
        else if (animator == null) playLoop()
    }

    private fun visibleEnough(view: View): Boolean =
        view.isShown &&
            view.getLocalVisibleRect(visible) &&
            visible.width() * visible.height() >= view.width * view.height * 0.5f

    private fun playLoop() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return
        val floatRobot = visibleEnough(binding.robotArt)
        val shineButton = visibleEnough(binding.cleanButton)
        if (!floatRobot && !shineButton) return
        val next =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val fraction = it.animatedFraction
                    // 周期函数首尾位姿和速度相同；只改变 RenderNode，不重新测量布局或解码图片。
                    if (floatRobot) {
                        binding.robotArt.translationY =
                            -lift * ((1 - cos(2 * PI * fraction)) / 2).toFloat()
                        binding.robotArt.rotation =
                            0.65f * (sin(2 * PI * fraction) * sin(PI * fraction)).toFloat()
                    }
                    // 光带覆盖整个周期，不设置起止空帧或淡出，下一圈从同一边框位置连续衔接。
                    binding.cleanButton.setShimmerProgress(if (shineButton) fraction else null)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (animator !== animation) return
                            animator = null
                            reset()
                            // 系统将动画倍率改成 0 时可能直接结束，不在回调中重启，避免空循环。
                        }
                    }
                )
            }
        animator = next
        next.start()
    }

    private fun reset() {
        binding.robotArt.translationY = 0f
        binding.robotArt.rotation = 0f
        binding.cleanButton.setShimmerProgress(null)
    }

    private fun stop() {
        val old = animator
        animator = null
        old?.removeAllUpdateListeners()
        old?.removeAllListeners()
        old?.cancel()
        reset()
        binding.cleanButton.stateListAnimator?.jumpToCurrentState()
        binding.cleanButton.scaleX = 1f
        binding.cleanButton.scaleY = 1f
    }
}
