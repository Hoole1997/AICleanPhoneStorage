package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.feature.home.ui.motion.HomeHeroMotion
import com.example.aicleanphonestorage.feature.home.data.HomeToolMetric
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemHomeHeroBinding
import com.example.aicleanphonestorage.databinding.ItemHomeSectionTitleBinding
import com.example.aicleanphonestorage.databinding.ItemHomeStatsBinding
import com.example.aicleanphonestorage.databinding.ItemHomeToolBinding
import java.text.NumberFormat

/** DiffUtil 只刷新变化的行。图片是预处理的小型本地 WebP，无网络请求或实时模糊；Hero 装饰动效由可见性与生命周期控制。 */
internal class HomeListAdapter(
    private val expanded: Boolean,
    private val actions: HomeUiActions,
    private val nativeContainer: ViewGroup? = null,
) : ListAdapter<HomeRow, RecyclerView.ViewHolder>(RowDiff) {
    private val heroes=mutableSetOf<HeroHolder>()
    private var motionActive=false
    fun setMotionActive(active:Boolean){motionActive=active;heroes.forEach{it.motion.setActive(active)}}
    fun refreshMotionVisibility(){heroes.forEach{it.motion.refreshVisibility()}}
    override fun onViewAttachedToWindow(holder:RecyclerView.ViewHolder){
        if(holder is HeroHolder){heroes+=holder;holder.motion.setActive(motionActive)}
    }
    override fun onViewDetachedFromWindow(holder:RecyclerView.ViewHolder){
        if(holder is HeroHolder){heroes-=holder;holder.motion.setActive(false)}
    }
    override fun onViewRecycled(holder:RecyclerView.ViewHolder){
        if(holder is HeroHolder){heroes-=holder;holder.motion.setActive(false)}
    }
    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HomeRow.Hero -> HERO
        is HomeRow.Statistics -> STATS
        HomeRow.NativeAd -> NATIVE
        HomeRow.Section -> SECTION
        is HomeRow.Tool -> TOOL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HERO -> HeroHolder(ItemHomeHeroBinding.inflate(inflater, parent, false), expanded, actions)
            STATS -> StatisticsHolder(ItemHomeStatsBinding.inflate(inflater, parent, false), expanded)
            NATIVE -> NativeHolder(FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            SECTION -> SectionHolder(ItemHomeSectionTitleBinding.inflate(inflater, parent, false))
            TOOL -> ToolHolder(ItemHomeToolBinding.inflate(inflater, parent, false), actions)
            else -> error("Unknown home row type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HomeRow.Hero -> (holder as HeroHolder).bind(row.content)
            is HomeRow.Statistics -> (holder as StatisticsHolder).bind(row.content)
            HomeRow.NativeAd -> (holder as NativeHolder).bind(requireNotNull(nativeContainer))
            HomeRow.Section -> Unit
            is HomeRow.Tool -> (holder as ToolHolder).bind(row.content)
        }
    }

    companion object {
        const val HERO = 0
        const val STATS = 1
        const val SECTION = 2
        const val TOOL = 3
        const val NATIVE = 4
    }
}

/** 广告容器属于页面，复用同一个实例；列表滚动/数据 Diff 不重新请求或持有多个 SDK 广告。 */
private class NativeHolder(private val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
    fun bind(container: ViewGroup) {
        if (container.parent === frame) return
        (container.parent as? ViewGroup)?.removeView(container)
        frame.removeAllViews()
        frame.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            // 间距属于首页排版；容器 GONE 时子 View 的 margin 不参与测量，不留下空广告行。
            bottomMargin = frame.resources.getDimensionPixelSize(R.dimen.home_section_gap)
        })
    }
}

private object RowDiff : DiffUtil.ItemCallback<HomeRow>() {
    override fun areItemsTheSame(oldItem: HomeRow, newItem: HomeRow) = oldItem.key == newItem.key
    override fun areContentsTheSame(oldItem: HomeRow, newItem: HomeRow) = oldItem == newItem
}

private class HeroHolder(
    private val binding: ItemHomeHeroBinding,
    expanded: Boolean,
    actions: HomeUiActions,
) : RecyclerView.ViewHolder(binding.root) {
    val motion=HomeHeroMotion(binding)
    init {
        binding.cleanButton.setOnClickListener { actions.onSmartClean() }
        if (expanded) {
            // 大字体或窄屏时采用自然增高的上下布局，保留原插画，不强行缩小文字或裁切数值。
            ConstraintSet().apply {
                clone(binding.heroLayout)
                clear(R.id.summary_column, ConstraintSet.END)
                connect(R.id.summary_column, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, itemView.dp(17))
                clear(R.id.robot_art, ConstraintSet.TOP)
                connect(R.id.robot_art, ConstraintSet.TOP, R.id.summary_column, ConstraintSet.BOTTOM, itemView.dp(16))
                connect(R.id.robot_art, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(binding.heroLayout)
            }
        }
    }

    fun bind(content: HeroContent) = with(binding) {
        val context = root.context
        val unknown = context.getString(R.string.home_unknown_value)
        summaryTitle.setText(when {
            content.virtualJunk -> R.string.home_space_ready
            content.scanComplete -> R.string.home_scan_complete
            else -> R.string.home_storage_used
        })
        summaryValue.text = content.value ?: unknown
        summaryUnit.text = content.unit
        scanStatus.text = when {
            content.virtualJunk -> context.getString(R.string.home_review_before_cleaning)
            content.scanComplete -> context.getString(R.string.home_junk_found)
            else -> context.getString(R.string.home_total_storage, content.totalCapacity ?: unknown)
        }
        val percentage = content.usedPercent?.let {
            NumberFormat.getPercentInstance(context.resources.configuration.locales[0]).format(it / 100.0)
        } ?: unknown
        usageLabel.text = if (content.virtualJunk) context.getString(R.string.home_junk_categories)
            else context.getString(R.string.home_used_percentage, percentage)
        // determinate ProgressBar，赋值不启动无限动画；文字提供 TalkBack 可读的百分比。
        usageProgress.progress = ((content.progressFraction ?: 0f).coerceIn(0f, 1f) * usageProgress.max).toInt()
    }
}

private class StatisticsHolder(private val binding: ItemHomeStatsBinding, expanded: Boolean) : RecyclerView.ViewHolder(binding.root) {
    init {
        with(binding) {
            downloadStat.statIcon.setImageResource(R.drawable.ic_home_download)
            downloadStat.statLabel.setText(R.string.home_download)
            availableStat.statIcon.setImageResource(R.drawable.ic_home_available)
            availableStat.statLabel.setText(R.string.home_available)
            usedStat.statIcon.setImageResource(R.drawable.ic_home_used)
            usedStat.statLabel.setText(R.string.home_used)
            if (expanded) {
                statisticsRow.orientation = LinearLayout.VERTICAL
                statisticsRow.setPadding(itemView.dp(16), itemView.dp(12), itemView.dp(16), itemView.dp(12))
                listOf(downloadStat.root, availableStat.root, usedStat.root).forEachIndexed { index, row ->
                    row.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    row.updateLayoutParams<LinearLayout.LayoutParams> {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        weight = 0f
                        topMargin = if (index == 0) 0 else itemView.dp(12)
                    }
                }
            }
        }
    }

    fun bind(content: HomeStatistics) = with(binding) {
        val unknown = root.context.getString(R.string.home_unknown_value)
        downloadStat.statValue.text = content.download ?: unknown
        availableStat.statValue.text = content.available ?: unknown
        usedStat.statValue.text = content.used ?: unknown
    }
}

private class SectionHolder(binding: ItemHomeSectionTitleBinding) : RecyclerView.ViewHolder(binding.root) {
    init { ViewCompat.setAccessibilityHeading(binding.root, true) }
}

private class ToolHolder(private val binding: ItemHomeToolBinding, private val actions: HomeUiActions) : RecyclerView.ViewHolder(binding.root) {
    fun bind(content: HomeToolItem) = with(binding) {
        toolTitle.setText(content.tool.titleRes)
        toolIcon.setImageResource(content.tool.iconRes)
        toolDetail.text = content.detail ?: when(val metric=content.metric){
            HomeToolMetric.Reading->root.context.getString(R.string.home_metric_reading)
            HomeToolMetric.NotScanned->root.context.getString(R.string.home_metric_scan_first)
            HomeToolMetric.AccessRequired->root.context.getString(R.string.home_metric_access)
            HomeToolMetric.Unavailable->root.context.getString(R.string.home_metric_unavailable)
            is HomeToolMetric.AppCount->root.resources.getQuantityString(if(metric.selected)R.plurals.home_selected_apps else R.plurals.home_app_count,metric.value,metric.value)
            else->root.context.getString(R.string.home_unknown_value)
        }
        // 整张卡片是点击目标，18dp 的箭头只是装饰，避免不合规的小触摸区域。
        root.contentDescription = "${toolTitle.text}, ${toolDetail.text}"
        root.setOnClickListener { actions.onToolSelected(content.tool) }
    }
}

internal fun View.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
