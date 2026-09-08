package com.example.aicleanphonestorage.core.ui.motion

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import kotlin.math.atan2
import kotlin.math.hypot

/** 原生胶囊按钮的边框流光。按真实圆角周长移动，不覆盖按钮中心、文字或图标。 */
class ShimmerPillButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val outline = Path()
    private val clip = Path()
    private val trail = Path()
    private val measure = PathMeasure()
    private val bounds = RectF()
    private val matrix = Matrix()
    private val head = FloatArray(2)
    private val tail = FloatArray(2)
    private val gradient =
        LinearGradient(
            0f,
            0f,
            1f,
            0f,
            intArrayOf(0x002878F5, 0x604CAEFF, 0xFF65CFFF.toInt(), 0xFF2878F5.toInt()),
            floatArrayOf(0f, 0.35f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
    private var progress: Float? = null
    private var perimeter = 0f
    private var trailLength = 0f

    /** 不拥有定时器；可见性和播放间隔由页面协调。同值不额外重绘。 */
    fun setShimmerProgress(value: Float?) {
        val next = value?.coerceIn(0f, 1f)
        if (progress == next) return
        progress = next
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        outline.reset()
        perimeter = 0f
        if (w <= 0 || h <= 0) return
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.addRoundRect(bounds, h / 2f, h / 2f, Path.Direction.CW)
        val inset = minOf(2f * density, h / 4f)
        bounds.inset(inset, inset)
        outline.addRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, Path.Direction.CW)
        measure.setPath(outline, true)
        perimeter = measure.length
        trailLength = perimeter * 0.2f
    }

    override fun onDraw(canvas: Canvas) {
        val frame = progress
        if (frame != null && isEnabled && !isPressed && perimeter > 0f) {
            // 距离沿 PathMeasure 周长推进，直边和圆角处都保持一致速度。
            val position = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - frame else frame
            val end = (position * perimeter) % perimeter
            val reverse = layoutDirection == LAYOUT_DIRECTION_RTL
            val tailDistance =
                (end + (if (reverse) trailLength else -trailLength) + perimeter) % perimeter
            val start = if (reverse) end else tailDistance
            val stop = if (reverse) tailDistance else end
            trail.rewind()
            if (start < stop) measure.getSegment(start, stop, trail, true)
            else {
                // 光带经过路径接缝时拆为两个子路径，避免边框某个位置突然断光。
                measure.getSegment(start, perimeter, trail, true)
                measure.getSegment(0f, stop, trail, true)
            }
            measure.getPosTan(tailDistance, tail, null)
            measure.getPosTan(end, head, null)
            val dx = head[0] - tail[0]
            val dy = head[1] - tail[1]
            matrix.setScale(hypot(dx, dy).coerceAtLeast(1f), 1f)
            matrix.postRotate(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
            matrix.postTranslate(tail[0], tail[1])
            gradient.setLocalMatrix(matrix)
            stroke.shader = gradient
            val save = canvas.save()
            canvas.clipPath(clip)
            // 两次细描边形成柔光；不使用实时模糊、离屏位图或逐帧创建 Shader/Path。
            stroke.strokeWidth = 4.8f * density
            stroke.alpha = 42
            canvas.drawPath(trail, stroke)
            stroke.strokeWidth = 1.7f * density
            stroke.alpha = 230
            canvas.drawPath(trail, stroke)
            canvas.restoreToCount(save)
        }
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        setShimmerProgress(null)
        super.onDetachedFromWindow()
    }
}
