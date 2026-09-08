package com.example.aicleanphonestorage.core.ui.empty

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R

/** 统一的 Figma 空列表组件。只有静态 WebP 与文案；小高度/大字体下允许滚动，不启动任何任务。 */
class EmptyStateView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ScrollView(context, attrs, defStyleAttr) {
    private var list: RecyclerView? = null
    private var observer: ViewTreeObserver? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { fitHeight() }

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        LayoutInflater.from(context).inflate(R.layout.view_empty_state_content, this, true)
    }

    /** 在带头部的 RecyclerView 中填满剩余高度；日期/权限说明仍可操作，空图不会挤在列表顶部。 */
    fun fitRemainingSpace(parent: RecyclerView) {
        if (list === parent) return
        stopFittingList()
        list = parent
        observer = parent.viewTreeObserver.also { it.addOnGlobalLayoutListener(layoutListener) }
        fitHeight()
    }

    private fun fitHeight() {
        val parent = list ?: return
        val position = parent.getChildAdapterPosition(this)
        if (position == RecyclerView.NO_POSITION || parent.height == 0) return
        var preceding = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) in 0 until position)
                preceding +=
                    parent.layoutManager?.getDecoratedMeasuredHeight(child) ?: child.measuredHeight
        }
        val remaining =
            (parent.height - parent.paddingTop - parent.paddingBottom - preceding).coerceAtLeast(
                (220 * resources.displayMetrics.density).toInt()
            )
        if (layoutParams.height != remaining) {
            layoutParams = layoutParams.apply { height = remaining }
        }
    }

    fun stopFittingList() {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observer = null
        list = null
    }

    override fun onDetachedFromWindow() {
        stopFittingList()
        super.onDetachedFromWindow()
    }
}
