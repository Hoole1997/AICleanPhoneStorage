package com.example.aicleanphonestorage.feature.startup

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

/** 只负责布局与原生循环进度动画；跳转仅由广告回调状态驱动。 */
internal class StartupRenderer(private val binding: ScreenStartupBinding) {
    private var motionEnabled = false
    private var resumed = false
    private var finished = false
    private var windowFocused = true
    private var surfaceShown = false
    val transitionsEnabled: Boolean get() = resumed && windowFocused && motionEnabled
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
        finished = state.ready || state.consumed
        updateProgress()
    }

    fun start() { resumed = true; if (windowFocused) motion.start() else updateProgress() }
    fun windowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (resumed && focused) motion.start() else motion.stop()
        updateProgress()
    }
    fun stop() { resumed = false; motion.stop(); updateProgress() }
    private fun motionChanged(enabled: Boolean) {
        motionEnabled = enabled
        updateProgress()
    }
    private fun updateProgress() {
        val animate = resumed && windowFocused && motionEnabled && !finished
        // INVISIBLE 保持占位并让 Material 停止内部动画；静态段不表达任何虚构的百分比。
        binding.startupProgress.visibility = if (animate) View.VISIBLE else View.INVISIBLE
        binding.startupProgressStatic.visibility = if (animate) View.GONE else View.VISIBLE
    }
    fun dispose() { stop(); binding.root.removeOnLayoutChangeListener(layoutListener) }
}
