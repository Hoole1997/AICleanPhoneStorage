package com.example.aicleanphonestorage.feature.retention

import android.graphics.Paint
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenUninstallRetentionBinding

/** 固定六个功能卡片复用布局；页面只处理测量、资源和状态，不读取渠道或请求广告。 */
internal class RetentionRenderer(private val binding: ScreenUninstallRetentionBinding) {
    val cards = listOf(binding.offer.junk, binding.offer.malware, binding.offer.similar,
        binding.offer.screenshots, binding.offer.large, binding.offer.notifications)
    val choices = listOf(binding.reason.reasonDifficult, binding.reason.reasonAds, binding.reason.reasonResults,
        binding.reason.reasonFeatures, binding.reason.reasonSlow, binding.reason.reasonOther)

    init {
        androidx.core.view.ViewCompat.setAccessibilityHeading(binding.offer.offerHeading, true)
        androidx.core.view.ViewCompat.setAccessibilityHeading(binding.reason.reasonHeading, true)
        val labels = listOf(R.string.retention_junk, R.string.retention_malware, R.string.retention_similar,
            R.string.retention_screenshots, R.string.retention_large, R.string.retention_notifications)
        val icons = listOf(R.drawable.retention_junk, R.drawable.retention_malware, R.drawable.retention_similar,
            R.drawable.retention_screenshots, R.drawable.retention_large, R.drawable.retention_notifications)
        cards.forEachIndexed { i, card ->
            card.featureIcon.setImageResource(icons[i])
            card.featureLabel.setText(labels[i])
            card.root.contentDescription = card.featureLabel.text
        }
        binding.reason.uninstall.paintFlags = binding.reason.uninstall.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.reason.feedback.hint = binding.root.context.getString(R.string.retention_feedback_hint,
            binding.root.context.getString(R.string.app_name))
        // 窄屏和大字体保留两列三行，文字转为图标下方，避免剩余文字宽度只有几个像素。
        val compact = binding.root.resources.configuration.screenWidthDp < 350 || binding.root.resources.configuration.fontScale >= 1.3f
        if (compact) cards.forEach { card ->
            card.root.orientation = android.widget.LinearLayout.VERTICAL
            card.root.gravity = android.view.Gravity.CENTER
            card.featureLabel.layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
            card.featureLabel.gravity = android.view.Gravity.CENTER
            // 箭头保留原版位置语义，不覆盖长文本。
            card.root.getChildAt(2).layoutParams = android.widget.LinearLayout.LayoutParams(dp(18), dp(18)).apply { topMargin = dp(6) }
        }
        binding.offer.hero.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val height = ((right - left) * 330f / 375f).toInt()
            if (height > 0 && view.layoutParams.height != height) {
                view.layoutParams = view.layoutParams.apply { this.height = height }
            }
        }
    }

    fun render(state: RetentionState, allowed: Boolean, externalBusy: Boolean) {
        val reason = state.page == RetentionPage.REASON
        binding.pages.displayedChild = if (reason) 1 else 0
        binding.reasonBackground.isVisible = reason
        val enabled = allowed && !state.busy && !externalBusy
        cards.forEach { it.root.isEnabled = enabled }
        listOf(binding.offer.offerKeep, binding.offer.uninstallAnyway, binding.reason.reasonKeep,
            binding.reason.uninstall, binding.reason.back).forEach { it.isEnabled = enabled }
        choices.forEach { it.isEnabled = enabled }
        binding.reason.feedback.isEnabled = enabled
    }

    private fun dp(value: Int) = (value * binding.root.resources.displayMetrics.density).toInt()
}
