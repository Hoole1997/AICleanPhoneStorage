package com.example.aicleanphonestorage.core.ui.completion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 有限数量的庆祝粒子和扩散圆环；固定数组/画笔，无逐帧对象、位图、模糊或独立循环。 */
class CompletionBurstView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val directions =
        FloatArray(32) { index ->
            val angle = (index / 2) * PI / 8
            (if (index % 2 == 0) cos(angle) else sin(angle)).toFloat()
        }
    private var progress = 1f

    fun frame(value: Float) {
        progress = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress >= 1f) return
        val size = minOf(width, height).toFloat()
        val fade = (1f - progress).coerceIn(0f, 1f)
        paint.color = Color.WHITE
        paint.alpha = (100 * fade).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density * 1.5f
        canvas.drawCircle(width / 2f, height / 2f, size * (0.22f + 0.25f * progress), paint)
        paint.style = Paint.Style.FILL
        for (i in 0 until 16) {
            val radius = size * (0.27f + progress * (if (i % 2 == 0) 0.2f else 0.15f))
            paint.color = if (i % 3 == 0) Color.rgb(175, 234, 255) else Color.WHITE
            paint.alpha = (220 * fade).toInt()
            canvas.drawCircle(
                width / 2f + directions[i * 2] * radius,
                height / 2f + directions[i * 2 + 1] * radius,
                resources.displayMetrics.density * (if (i % 2 == 0) 3f else 2f),
                paint,
            )
        }
    }
}
