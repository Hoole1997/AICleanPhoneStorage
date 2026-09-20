package com.example.aicleanphonestorage.feature.battery.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.example.aicleanphonestorage.R
import java.text.NumberFormat

/** 原始 Figma 轮廓 + 动态电量填充。所有 Path/Paint 在初始化时建立，onDraw 不分配图像或启动动画。 */
class BatteryGaugeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val arc =
        requireNotNull(
            PathParser.createPathFromPathData(context.getString(R.string.battery_gauge_arc_path))
        )
    private val outline =
        requireNotNull(
            PathParser.createPathFromPathData(
                context.getString(R.string.battery_gauge_outline_path)
            )
        )
    private val bolt =
        requireNotNull(
            PathParser.createPathFromPathData(context.getString(R.string.battery_gauge_bolt_path))
        )
    private val inner =
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.battery_inner_ring)).apply {
            setBounds(21, 21, 167, 150)
        }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 96, 241) }
    private val outlinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(127, 171, 245) }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fill = RectF()
    private val percentText =
        AppCompatTextView(context).apply {
            setTextAppearance(R.style.Widget_Home_Text_Bold)
            textSize = 24f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            includeFontPadding = false
            maxLines = 1
            text = context.getString(R.string.home_unknown_value)
        }
    private val numbers =
        NumberFormat.getPercentInstance(resources.configuration.locales[0]).apply {
            maximumFractionDigits = 0
        }
    private var labelTop = 0
    private var percent: Int? = null
    private var charging: Boolean? = null
    private var powerConnected: Boolean? = null
    private var rendered = false
    private val gaugeSize
        get() = resources.getDimensionPixelSize(R.dimen.battery_gauge_size)

    private val textTop
        get() = resources.getDimensionPixelSize(R.dimen.battery_gauge_text_top)

    init {
        setWillNotDraw(false)
        addView(percentText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        render(null, null, null)
    }

    fun render(level: Int?, isCharging: Boolean?, isPowerConnected: Boolean?) {
        val value = level?.takeIf { it in 0..100 }
        if (
            rendered &&
                percent == value &&
                charging == isCharging &&
                powerConnected == isPowerConnected
        )
            return
        rendered = true
        percent = value
        charging = isCharging
        powerConnected = isPowerConnected
        val color =
            when {
                value == null -> Color.rgb(103, 113, 132)
                value <= 20 -> Color.rgb(245, 93, 70)
                else -> Color.rgb(13, 96, 241)
            }
        levelPaint.color = color
        percentText.setTextColor(color)
        percentText.text =
            value?.let { numbers.format(it / 100.0) }
                ?: context.getString(R.string.home_unknown_value)
        fill.set(20f, 53f - 42.5f * ((value ?: 0) / 100f), 40f, 53f)
        contentDescription =
            context.getString(
                R.string.battery_level_description,
                percentText.text,
                context.getString(
                    when {
                        charging == true -> R.string.battery_charging
                        powerConnected == true -> R.string.battery_power_connected
                        charging == false -> R.string.battery_not_charging
                        else -> R.string.home_unknown_value
                    }
                ),
            )
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        percentText.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val desiredWidth =
            maxOf(
                gaugeSize,
                percentText.measuredWidth + (16 * resources.displayMetrics.density).toInt(),
            )
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        percentText.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        // 百分比自然字体高度决定视图下界，大字号不会挤入电池图形或被固定 187dp 裁切。
        labelTop =
            if (resources.configuration.fontScale >= 1.5f) (gaugeSize * 0.92f).toInt() else textTop
        val height =
            maxOf(
                gaugeSize,
                labelTop +
                    percentText.measuredHeight +
                    (14 * resources.displayMetrics.density).toInt(),
            )
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        percentText.layout(0, labelTop, width, labelTop + percentText.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate((width - gaugeSize) / 2f, 0f)
        canvas.scale(gaugeSize / 187f, gaugeSize / 187f)
        canvas.drawPath(arc, ringPaint)
        inner.draw(canvas)
        canvas.translate(64f, 53f) // 原 Figma 60×60 电池轮廓相对 187×187 仪表的位置。
        canvas.drawPath(outline, outlinePaint)
        if ((percent ?: 0) > 0) canvas.drawRoundRect(fill, 2f, 2f, levelPaint)
        // 闪电表示连接供电；充电保护/暂停充电也显示，不把插电状态误当成电量正在增加。
        if (powerConnected == true || charging == true) {
            canvas.drawPath(bolt, levelPaint)
            val clip = canvas.save()
            canvas.clipRect(fill)
            canvas.drawPath(bolt, whitePaint)
            canvas.restoreToCount(clip)
        }
        canvas.restoreToCount(save)
    }
}
