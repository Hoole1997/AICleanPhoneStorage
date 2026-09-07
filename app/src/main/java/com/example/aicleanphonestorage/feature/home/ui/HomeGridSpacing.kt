package com.example.aicleanphonestorage.feature.home.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R

/** 层级间距独立于 ViewHolder；相邻卡片各承担半个 gutter，页面边缘由 RecyclerView padding 管理。 */
internal class HomeGridSpacing : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val adapter = parent.adapter ?: return
        val gap = parent.resources.getDimensionPixelSize(R.dimen.home_grid_gap)
        val sectionGap = parent.resources.getDimensionPixelSize(R.dimen.home_section_gap)
        when (adapter.getItemViewType(position)) {
            HomeListAdapter.HERO, HomeListAdapter.STATS -> outRect.bottom = sectionGap
            HomeListAdapter.SECTION -> outRect.bottom = parent.dp(16)
            HomeListAdapter.TOOL -> {
                val manager = parent.layoutManager as GridLayoutManager
                val column = manager.spanSizeLookup.getSpanIndex(position, manager.spanCount)
                val start = if (column == 0) 0 else gap / 2
                val end = if (column == manager.spanCount - 1) 0 else gap / 2
                if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    outRect.left = end; outRect.right = start
                } else {
                    outRect.left = start; outRect.right = end
                }
                outRect.bottom = gap
            }
        }
    }
}
