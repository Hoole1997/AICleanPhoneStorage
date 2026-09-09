package com.example.aicleanphonestorage.feature.startup

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.motion.MotionPreferences
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding

/** 只负责布局与有限动画；真实跳转由 ViewModel 的单调时钟驱动，不依赖动画回调或系统动画倍率。 */
internal class StartupRenderer(private val binding: ScreenStartupBinding, private val progress: () -> Int) {
    private var animator: ValueAnimator? = null
    private var resumed = false
    private var finished = false
    private var windowFocused = true
    private var surfaceShown = false
    private var bars = Insets.NONE
    private val motion = MotionPreferences(binding.root.context, ::motionChanged)
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionBrand() }

    init {
        val title = binding.startupTitle.text.toString()
        binding.startupTitle.text = SpannableString(title).apply {
            if (title.startsWith("AI")) setSpan(ForegroundColorSpan(binding.root.context.getColor(R.color.startup_blue)), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        ViewCompat.setAccessibilityHeading(binding.startupTitle, true)
        binding.root.addOnLayoutChangeListener(layoutListener)
    }

    fun insets(value: Insets) {
        if (bars == value) return
        bars = value
        val brandParams = binding.startupBrand.layoutParams as ViewGroup.MarginLayoutParams
        brandParams.leftMargin = dimension(R.dimen.startup_brand_start) + value.left
        brandParams.rightMargin = dimension(R.dimen.startup_brand_end) + value.right
        binding.startupBrand.layoutParams = brandParams
        val params = binding.startupFooter.layoutParams as ViewGroup.MarginLayoutParams
        val side = dimension(R.dimen.startup_footer_side)
        params.leftMargin = side + value.left
        params.rightMargin = side + value.right
        params.bottomMargin = dimension(R.dimen.startup_footer_bottom) + value.bottom
        binding.startupFooter.layoutParams = params
        positionBrand()
    }

    private fun dimension(id: Int) = binding.root.resources.getDimensionPixelSize(id)

    private fun positionBrand() {
        if (binding.root.height == 0 || binding.startupBrand.height == 0) return
        val brand = binding.startupBrand
        val gap = dimension(R.dimen.startup_content_gap)
        val latestTop = binding.startupFooter.top - gap - brand.height
        val adjustedTop = minOf(brand.top, latestTop).coerceAtLeast(bars.top + gap)
        brand.translationY = (adjustedTop - brand.top).toFloat()
        // 极小屏/大字体将品牌上移时增加柔和底色，保持文字在插画上的对比度；标准尺寸完全按稿。
        val showSurface = adjustedTop < brand.top
        if (showSurface != surfaceShown) {
            surfaceShown = showSurface
            brand.background = if (showSurface)
                GradientDrawable().apply { setColor(0xF0EDF6FF.toInt()); cornerRadius = gap.toFloat() }
            else null
        }
    }

    fun render(state: StartupState) {
        finished = state.ready
        if (finished) { animator?.cancel(); animator = null }
        binding.startupProgress.setProgressCompat(if (finished) 1000 else progress(), false)
    }

    fun start() { resumed = true; motion.start() }
    fun windowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (resumed) motion.start()
    }
    fun stop() { resumed = false; motion.stop(); animator?.cancel(); animator = null }
    private fun motionChanged(enabled: Boolean) {
        animator?.cancel()
        animator = null
        if (!resumed || !windowFocused || !enabled || finished) return
        animator = ValueAnimator.ofInt(0, 1000).apply {
            duration = StartupViewModel.MAXIMUM_MS
            addUpdateListener { binding.startupProgress.setProgressCompat(progress(), false) }
            start()
        }
    }
    fun dispose() { stop(); binding.root.removeOnLayoutChangeListener(layoutListener) }
}
