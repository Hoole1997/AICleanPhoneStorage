package com.example.aicleanphonestorage.feature.networktraffic.ui

import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemTrafficAppBinding
import com.example.aicleanphonestorage.databinding.ItemTrafficEmptyBinding
import com.example.aicleanphonestorage.databinding.ItemTrafficHeaderBinding
import com.example.aicleanphonestorage.feature.networktraffic.data.*
import java.text.NumberFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface TrafficRow {
    val key: String
    data class Header(val period: TrafficPeriod, val mobile: NetworkUsage, val wifi: NetworkUsage) : TrafficRow { override val key = "header" }
    data class App(val app: TrafficApp, val total: Long) : TrafficRow { override val key = "uid:${app.uid}" }
    data object Empty : TrafficRow { override val key = "empty" }
}

internal class TrafficListAdapter(
    private val scope: CoroutineScope,
    private val icons: AppIconLoader,
    private val onPeriod: (TrafficPeriod) -> Unit,
    private val onManage: (TrafficApp) -> Unit,
) : ListAdapter<TrafficRow, RecyclerView.ViewHolder>(TrafficRowDiff) {
    private val attached = mutableSetOf<AppHolder>()
    private var active = false
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }
    override fun getItemId(position: Int): Long = when (val row = getItem(position)) {
        is TrafficRow.Header -> Long.MIN_VALUE
        is TrafficRow.App -> row.app.uid.toLong()
        TrafficRow.Empty -> Long.MIN_VALUE + 1
    }

    fun submit(snapshot: TrafficSnapshot, selected: TrafficPeriod, showEmpty:Boolean=true, onCommitted: () -> Unit = {}) {
        submitList(buildList {
            add(TrafficRow.Header(selected, snapshot.mobile, snapshot.wifi))
            snapshot.apps.forEach { add(TrafficRow.App(it, snapshot.totalBytes)) }
            if (showEmpty && snapshot.apps.isEmpty()) add(TrafficRow.Empty)
        }, onCommitted)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) { is TrafficRow.Header -> 0; is TrafficRow.App -> 1; TrafficRow.Empty -> 2 }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderHolder(ItemTrafficHeaderBinding.inflate(inflater, parent, false))
            1 -> AppHolder(ItemTrafficAppBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(ItemTrafficEmptyBinding.inflate(inflater, parent, false))
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = bind(holder, position, TrafficRowDiff.ALL)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val fields = if (payloads.isEmpty()) TrafficRowDiff.ALL else payloads.filterIsInstance<Int>().fold(0) { result, value -> result or value }
        bind(holder, position, fields)
    }
    private fun bind(holder: RecyclerView.ViewHolder, position: Int, fields: Int) {
        when (val row = getItem(position)) {
            is TrafficRow.Header -> (holder as HeaderHolder).bind(row, fields)
            is TrafficRow.App -> (holder as AppHolder).bind(row, fields)
            TrafficRow.Empty -> Unit
        }
    }
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if(holder is EmptyHolder)holder.binding.root.fitRemainingSpace(holder.itemView.parent as RecyclerView)
        if (holder is AppHolder) { attached += holder; if (active) holder.loadIcon() }
    }
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if(holder is EmptyHolder)holder.binding.root.stopFittingList()
        if (holder is AppHolder) { attached -= holder; holder.pauseIcon() }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AppHolder) holder.stopIcon()
    }
    fun setActive(value: Boolean) {
        active = value
        attached.forEach { if (value) it.loadIcon() else it.stopIcon() }
        if (!value) icons.clear()
    }

    private inner class HeaderHolder(private val binding: ItemTrafficHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            listOf(binding.periodMonth, binding.periodPrevious, binding.periodDay).forEach { it.isCheckable = true }
            binding.periodMonth.setOnClickListener { onPeriod(TrafficPeriod.THIS_MONTH) }
            binding.periodPrevious.setOnClickListener { onPeriod(TrafficPeriod.LAST_MONTH) }
            binding.periodDay.setOnClickListener { onPeriod(TrafficPeriod.LAST_24_HOURS) }
            binding.mobileTotal.totalIcon.setImageResource(R.drawable.ic_traffic_mobile)
            binding.mobileTotal.totalLabel.setText(R.string.traffic_mobile)
            binding.wifiTotal.totalIcon.setImageResource(R.drawable.ic_traffic_wifi)
            binding.wifiTotal.totalLabel.setText(R.string.traffic_wifi)
            ViewCompat.setAccessibilityHeading(binding.appsHeading, true)
        }
        fun bind(row: TrafficRow.Header, fields: Int) = with(binding) {
            if (fields and TrafficRowDiff.PERIOD != 0) periodGroup.check(when (row.period) {
                TrafficPeriod.THIS_MONTH -> R.id.period_month
                TrafficPeriod.LAST_MONTH -> R.id.period_previous
                TrafficPeriod.LAST_24_HOURS -> R.id.period_day
            })
            if (fields and TrafficRowDiff.MOBILE != 0) {
                val mobile = bytes(root.context, row.mobile.bytes)
                mobileTotal.totalValue.text = mobile.first; mobileTotal.totalUnit.text = mobile.second
            }
            if (fields and TrafficRowDiff.WIFI != 0) {
                val wifi = bytes(root.context, row.wifi.bytes)
                wifiTotal.totalValue.text = wifi.first; wifiTotal.totalUnit.text = wifi.second
            }
            if (fields and (TrafficRowDiff.MOBILE or TrafficRowDiff.WIFI) != 0) {
                trafficNotice.isVisible = row.mobile.bytes == null || row.wifi.bytes == null
                trafficNotice.setText(when (row.mobile.availability) {
                    UsageAvailability.PHONE_PERMISSION_REQUIRED -> R.string.traffic_phone_needed
                    UsageAvailability.NO_SIM -> R.string.traffic_no_sim
                    else -> R.string.traffic_partial
                })
            }
        }
    }

    private inner class AppHolder(private val binding: ItemTrafficAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private var row: TrafficRow.App? = null
        private var iconJob: Job? = null
        init {
            if (itemView.resources.configuration.fontScale > 1.2f || itemView.resources.configuration.screenWidthDp < 360) {
                // 名称与数值上下排布，Manage 下移；保留完整流量数值，不让按钮挤成零宽文本。
                binding.appNameRow.orientation = LinearLayout.VERTICAL
                binding.appLabel.updateLayoutParams<LinearLayout.LayoutParams> { width = ViewGroup.LayoutParams.MATCH_PARENT; weight = 0f }
                binding.appBytes.updateLayoutParams<LinearLayout.LayoutParams> { marginStart = 0; topMargin = dp(6) }
                ConstraintSet().apply {
                    clone(binding.root)
                    clear(R.id.app_metrics, ConstraintSet.END); clear(R.id.app_metrics, ConstraintSet.BOTTOM)
                    connect(R.id.app_metrics, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    clear(R.id.app_manage, ConstraintSet.TOP)
                    connect(R.id.app_manage, ConstraintSet.TOP, R.id.app_metrics, ConstraintSet.BOTTOM, dp(8))
                    clear(R.id.app_icon, ConstraintSet.BOTTOM)
                    applyTo(binding.root)
                }
            }
            binding.appManage.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) (getItem(position) as? TrafficRow.App)?.app?.let(onManage)
            }
        }
        fun bind(value: TrafficRow.App, fields: Int) {
            val previous = row
            row = value
            val iconChanged = previous == null || previous.app.uid != value.app.uid ||
                previous.app.packages.firstOrNull()?.packageName != value.app.packages.firstOrNull()?.packageName
            if (fields and TrafficRowDiff.IDENTITY != 0) {
                binding.appLabel.text = appName(itemView.context, value.app)
                binding.appLabel.contentDescription = binding.appLabel.text
                binding.appManage.isEnabled = value.app.packages.isNotEmpty()
                binding.appManage.contentDescription = itemView.context.getString(R.string.traffic_manage_app, binding.appLabel.text)
            }
            if (fields and TrafficRowDiff.BYTES != 0)
                binding.appBytes.text = bytes(itemView.context, value.app.bytes).let { "${it.first} ${it.second}" }
            if (fields and TrafficRowDiff.SHARE != 0)
                binding.appUsageBar.setProgress(TrafficRowDiff.share(value), previous?.app?.uid == value.app.uid && fields != TrafficRowDiff.ALL)
            // 仅图标身份变化才清空并重载。数值/排序改变时保留当前 Drawable，避免整表闪回占位图。
            if (iconChanged) {
                stopIcon()
                if (active && itemView.isAttachedToWindow) loadIcon()
            }
        }
        fun loadIcon() {
            iconJob?.cancel()
            val data = row?.app ?: return
            val packageName = data.packages.firstOrNull()?.packageName ?: return
            iconJob = scope.launch {
                val icon = icons.load(packageName)
                if (row?.app?.uid == data.uid && active && icon != null) binding.appIcon.setImageBitmap(icon)
            }
        }
        fun pauseIcon() { iconJob?.cancel(); iconJob = null }
        fun stopIcon() { pauseIcon(); binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon) }
        private fun dp(value: Int) = (value * itemView.resources.displayMetrics.density).toInt()
    }
    private class EmptyHolder(val binding: ItemTrafficEmptyBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        internal fun bytes(context: Context, count: Long?): Pair<String, String> {
            if (count == null) return context.getString(R.string.home_unknown_value) to ""
            val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
            var amount = count.toDouble(); var index = 0
            while (amount >= 1000 && index < units.lastIndex) { amount /= 1000; index++ }
            val formatter = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply { maximumFractionDigits = 2; isGroupingUsed = false }
            return formatter.format(amount) to units[index]
        }
        internal fun appName(context: Context, app: TrafficApp): String = when {
            app.packages.size > 1 -> context.getString(R.string.traffic_shared_name, app.packages.first().label, app.packages.size - 1)
            app.packages.size == 1 -> app.packages.first().label
            app.uid < 0 -> context.getString(R.string.traffic_removed_uid)
            app.uid % 100000 < 10000 -> context.getString(R.string.traffic_system_uid, app.uid)
            else -> context.getString(R.string.traffic_unknown_uid, app.uid)
        }
    }
}
