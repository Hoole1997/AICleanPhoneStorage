package com.example.aicleanphonestorage.core.permissions

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView

/** 原生双按钮布局：正常字号按 Figma 并排，长翻译/大字号按文字实际宽度改为纵排，不截断按钮。 */
class PermissionActionsLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val gap = (13 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val available = (width - gap) / 2
        val vertical =
            resources.configuration.fontScale >= 1.5f ||
                (0 until childCount).any { index ->
                    val button = getChildAt(index) as? TextView ?: return@any false
                    Layout.getDesiredWidth(button.text, button.paint) +
                        button.compoundPaddingLeft +
                        button.compoundPaddingRight > available
                }
        val desired = if (vertical) VERTICAL else HORIZONTAL
        if (orientation != desired) orientation = desired
        // 只在测量时调整布局参数，无逐帧监听、轮询或持续重绘。
        for (index in 0 until childCount) {
            val params = getChildAt(index).layoutParams as LayoutParams
            params.width = if (vertical) LayoutParams.MATCH_PARENT else 0
            params.weight = if (vertical) 0f else 1f
            params.marginEnd = if (!vertical && index == 0) gap else 0
            params.topMargin = if (vertical && index > 0) gap else 0
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
