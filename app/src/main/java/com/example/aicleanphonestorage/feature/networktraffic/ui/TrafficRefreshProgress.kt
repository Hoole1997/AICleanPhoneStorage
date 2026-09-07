package com.example.aicleanphonestorage.feature.networktraffic.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 页内刷新动效，与真实查询和数据提交解耦。快查询立即更新列表，但指示器完成一轮后才隐藏。
 * 查询未完成只靠近90%，数据通过Diff提交后才补到100%。切换新请求/退后台会取消旧收尾。
 */
internal class TrafficRefreshProgress(private val indicator: LinearProgressIndicator, private val scope: CoroutineScope) {
    private var requestId: Long? = null
    private var startedAt = 0L
    private var animator: ValueAnimator? = null
    private var finishing: Job? = null
    private var active = false

    init { indicator.isIndeterminate = false; indicator.max = 1000 }

    fun start(id: Long) {
        if (!active || requestId == id) return
        finishing?.cancel()
        cancelAnimator()
        requestId = id
        startedAt = SystemClock.uptimeMillis()
        if (!indicator.isVisible) indicator.progress = 0
        indicator.isVisible = true
        // 连续切换时不隐藏/回退当前条，旧请求的完成回调也不能隐藏新请求。
        animator = animation(maxOf(indicator.progress, 900), 1200).also { it.start() }
    }

    fun complete() {
        val id = requestId ?: return
        if (!active || finishing?.isActive == true) return
        finishing = scope.launch {
            // 一轮最少约900ms（600ms推进 + 220ms收尾 + 80ms完整帧），不延迟真实数据。
            val wait = (600 - (SystemClock.uptimeMillis() - startedAt)).coerceAtLeast(0)
            if (wait > 0) delay(wait)
            if (requestId != id) return@launch
            finishAnimation()
            delay(80)
            if (requestId == id) {
                indicator.isVisible = false
                requestId = null
            }
        }
    }

    private suspend fun finishAnimation() = suspendCancellableCoroutine { continuation ->
        cancelAnimator()
        val finish = animation(1000, 220)
        animator = finish
        finish.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { if (continuation.isActive) continuation.resume(Unit) }
            override fun onAnimationCancel(animation: Animator) { continuation.cancel() }
        })
        continuation.invokeOnCancellation { finish.removeAllListeners(); finish.cancel() }
        finish.start()
    }

    private fun animation(target: Int, durationMillis: Long) = ValueAnimator.ofInt(indicator.progress, target).apply {
        duration = durationMillis
        interpolator = DecelerateInterpolator()
        addUpdateListener { indicator.progress = it.animatedValue as Int }
    }
    private fun cancelAnimator() { animator?.removeAllListeners(); animator?.cancel(); animator = null }
    fun abort() {
        finishing?.cancel(); finishing = null
        cancelAnimator()
        requestId = null
        indicator.isVisible = false
    }
    fun setActive(value: Boolean) { active = value; if (!value) abort() }
}
