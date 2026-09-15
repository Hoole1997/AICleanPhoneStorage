package com.example.aicleanphonestorage.core.ui.loading

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.DialogTaskLoadingBinding
import java.text.NumberFormat

/** 由弹框 View 生命周期持有；一个 Animator 同帧更新容量、进度条和百分比。 */
internal class LoadingCapacityRenderer(private val binding: DialogTaskLoadingBinding) {
    private val locale = binding.root.resources.configuration.locales[0]
    private val capacityFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }
    private val percentFormat = NumberFormat.getPercentInstance(locale)
    private var animator: ValueAnimator? = null
    private var displayedBytes = 0.0
    private var displayedPercent = 0.0
    private var target: Pair<Long, Int>? = null

    fun render(bytes: Long, percent: Int, animate: Boolean) {
        val next = bytes.coerceAtLeast(0) to percent.coerceIn(0, 100)
        if (target == next) return
        val first = target == null
        target = next
        animator?.cancel()
        if (first || !animate || !ValueAnimator.areAnimatorsEnabled() || next.second == 100) {
            // 完成帧立即对齐真实结果；系统关闭动画时仍准确更新，不延长任务生命周期。
            draw(next.first.toDouble(), next.second.toDouble())
            return
        }
        val startBytes = displayedBytes
        val startPercent = displayedPercent
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 100L
            interpolator = LinearInterpolator()
            addUpdateListener {
                val fraction = it.animatedFraction.toDouble()
                draw(
                    startBytes + (next.first - startBytes) * fraction,
                    startPercent + (next.second - startPercent) * fraction,
                )
            }
            start()
        }
    }

    private fun draw(bytes: Double, percent: Double) {
        displayedBytes = bytes
        displayedPercent = percent
        // 始终固定 MB、一位小数；避免 B/KB/GB 自动切换打断数字增长。
        binding.loadingCapacity.text = binding.root.context.getString(
            R.string.task_loading_megabytes, capacityFormat.format(bytes / 1_000_000.0),
        )
        binding.loadingProgress.isIndeterminate = false
        binding.loadingProgress.max = 10_000
        binding.loadingProgress.setProgressCompat((percent * 100).toInt(), false)
        binding.loadingPercentage.text = percentFormat.format(percent / 100)
    }

    fun stop() {
        animator?.cancel()
        animator = null
        target = null
    }
}
