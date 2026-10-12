package com.example.aicleanphonestorage.feature.speedtest.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.aicleanphonestorage.R

/**
 * Figma 6554:786 的环形进度：底环 #E3EDFF、内圆 #F1F6FF、进度弧 #B9D3FF→#0D60F1 圆头。
 * 设计稿是静态 270° 弧；这里改为显示真实测速进度，从 9 点方向顺时针增长，与稿件起点一致。
 * 只在进度变化时短暂插值重绘，没有常驻动画；页面不可见时由系统停止绘制。
 */
class SpeedTestRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    // 设计尺寸：188dp 外框，描边 10.444dp，底环半径 88.778dp，内圆半径 83.556dp。
    private val strokeWidth = 10.444f * density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SpeedTestRingView.strokeWidth
        color = ContextCompat.getColor(context, R.color.speed_ring_track)
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.speed_ring_inner)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SpeedTestRingView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val arcStart = ContextCompat.getColor(context, R.color.speed_ring_arc_start)
    private val arcEnd = ContextCompat.getColor(context, R.color.speed_ring_arc_end)
    private val bounds = RectF()
    private var displayed = 0f
    private var animator: ValueAnimator? = null

    /** 真实进度 0..1；变化时 200ms 线性补间，系统关闭动画时直接跳到目标值。 */
    fun setProgress(value: Float) {
        val target = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (target < displayed || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow) {
            displayed = target
            invalidate()
            return
        }
        if (target == displayed) return
        animator = ValueAnimator.ofFloat(displayed, target).apply {
            duration = 200
            interpolator = LinearInterpolator()
            addUpdateListener { displayed = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (188 * density).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val radius = minOf(w, h) / 2f - strokeWidth / 2f
        bounds.set(w / 2f - radius, h / 2f - radius, w / 2f + radius, h / 2f + radius)
        // 渐变沿弧从起点（180°）旋转；尾段留浅色，避免起点圆头取到深色末端。
        arc.shader = SweepGradient(w / 2f, h / 2f, intArrayOf(arcStart, arcEnd, arcStart), floatArrayOf(0f, 0.97f, 1f))
            .apply { setLocalMatrix(Matrix().apply { setRotate(START_ANGLE, w / 2f, h / 2f) }) }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, bounds.width() / 2f, track)
        canvas.drawCircle(cx, cy, 83.556f * density * minOf(width, height) / (188 * density), inner)
        if (displayed > 0f) canvas.drawArc(bounds, START_ANGLE, 360f * displayed, false, arc)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val START_ANGLE = 180f
    }
}
