package com.example.aicleanphonestorage.core.ui.loading

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** 窗口使用 wrap_content 接收异步广告高度变化；内容过高时滚动，避免固定旧高度裁切广告。 */
class LoadingDialogScrollView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit =
            (resources.displayMetrics.heightPixels - 80 * resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(1)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val maximum =
            if (mode == MeasureSpec.UNSPECIFIED) limit
            else minOf(limit, MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(
                maximum,
                if (mode == MeasureSpec.EXACTLY) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST,
            ),
        )
    }
}
