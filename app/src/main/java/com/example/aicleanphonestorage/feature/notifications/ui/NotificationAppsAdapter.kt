package com.example.aicleanphonestorage.feature.notifications.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ItemNotificationAppBinding
import com.example.aicleanphonestorage.databinding.ItemNotificationEmptyBinding
import com.example.aicleanphonestorage.databinding.ItemNotificationHeaderBinding
import com.example.aicleanphonestorage.feature.notifications.data.NotificationApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface NotificationRow {
    val key: String
    data class Header(val connected: Boolean, val rulesReady: Boolean, val error: Boolean, val empty:Boolean=false) : NotificationRow { override val key = "header" }
    data class App(val app: NotificationApp, val checked: Boolean, val enabled: Boolean) : NotificationRow { override val key = app.packageName }
    data object Empty : NotificationRow { override val key = "empty" }
}

/** 勾选只更新Switch的payload，不重绑名称/图标，不重新排序或重建整张列表。 */
internal class NotificationAppsAdapter(
    private val scope: CoroutineScope,
    private val icons: AppIconLoader,
    private val toggle: (String, Boolean) -> Unit,
    private val connectionAction: () -> Unit,
) : ListAdapter<NotificationRow, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<NotificationRow>() {
    override fun areItemsTheSame(oldItem: NotificationRow, newItem: NotificationRow) = oldItem.key == newItem.key
    override fun areContentsTheSame(oldItem: NotificationRow, newItem: NotificationRow) = oldItem == newItem
    override fun getChangePayload(oldItem: NotificationRow, newItem: NotificationRow): Any? =
        if (oldItem is NotificationRow.App && newItem is NotificationRow.App && oldItem.app == newItem.app) SELECTION else null
}) {
    private val attached = mutableSetOf<AppHolder>()
    private var active = false
    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    fun submit(state: NotificationUiState) {
        val catalog = state.catalog ?: return
        submitList(buildList {
            add(NotificationRow.Header(state.listenerConnected, state.rulesLoaded, state.saveError > 0 && !state.rulesLoaded, empty=catalog.apps.isEmpty()))
            catalog.apps.forEach { app ->
                val checked = state.saving[app.packageName] ?: (app.packageName in state.selected)
                add(NotificationRow.App(app, checked, state.rulesLoaded && app.packageName !in state.saving && (app.installed || checked)))
            }
            if (state.phase==NotificationPhase.Ready && catalog.apps.isEmpty()) add(NotificationRow.Empty)
        })
    }
    override fun getItemViewType(position: Int) = when (getItem(position)) { is NotificationRow.Header -> 0; is NotificationRow.App -> 1; NotificationRow.Empty -> 2 }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderHolder(ItemNotificationHeaderBinding.inflate(inflater, parent, false))
            1 -> AppHolder(ItemNotificationAppBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(ItemNotificationEmptyBinding.inflate(inflater, parent, false))
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = bind(holder, position, false)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) = bind(holder, position, payloads.isNotEmpty())
    private fun bind(holder: RecyclerView.ViewHolder, position: Int, selectionOnly: Boolean) {
        when (val row = getItem(position)) {
            is NotificationRow.Header -> (holder as HeaderHolder).bind(row)
            is NotificationRow.App -> (holder as AppHolder).bind(row, selectionOnly)
            NotificationRow.Empty -> Unit
        }
    }
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if(holder is EmptyHolder)holder.binding.root.fitRemainingSpace(holder.itemView.parent as RecyclerView)
        if (holder is AppHolder) { attached.add(holder); if (active) holder.loadIcon() }
    }
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if(holder is EmptyHolder)holder.binding.root.stopFittingList()
        if (holder is AppHolder) { attached.remove(holder); holder.pauseIcon() }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) { if (holder is AppHolder) holder.clearIcon() }
    fun setActive(value: Boolean) {
        active = value
        attached.forEach { if (value) it.loadIcon() else it.clearIcon() }
        if (!value) icons.clear()
    }
    private inner class HeaderHolder(private val binding: ItemNotificationHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        init { ViewCompat.setAccessibilityHeading(binding.notificationIntro, true); binding.notificationConnectionMessage.setOnClickListener { connectionAction() } }
        fun bind(row: NotificationRow.Header) {
            binding.notificationIntro.isVisible=!row.empty
            binding.notificationConnectionMessage.isVisible = row.error || (row.rulesReady && !row.connected)
            binding.notificationConnectionMessage.setText(if (row.error) R.string.notification_rule_unavailable else R.string.notification_connection_wait)
        }
    }
    private inner class AppHolder(private val binding: ItemNotificationAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private var row: NotificationRow.App? = null
        private var iconJob: Job? = null
        init {
            binding.root.setOnClickListener { if (binding.notificationAppSwitch.isEnabled) binding.notificationAppSwitch.performClick() }
        }
        fun bind(value: NotificationRow.App, selectionOnly: Boolean) {
            val iconChanged = row?.app?.packageName != value.app.packageName || row?.app?.installed != value.app.installed
            row = value
            if (!selectionOnly) {
                binding.notificationAppName.text = value.app.label
                binding.notificationAppSwitch.contentDescription = itemView.context.getString(R.string.notification_auto_clear_app, value.app.label)
            }
            // 先卸下回调，再绑定状态，防止RecyclerView复用/DataStore回放误触发规则写入。
            binding.notificationAppSwitch.setOnCheckedChangeListener(null)
            binding.notificationAppSwitch.isChecked = value.checked
            binding.notificationAppSwitch.isEnabled = value.enabled
            binding.notificationAppSwitch.setOnCheckedChangeListener { _, checked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) (getItem(position) as? NotificationRow.App)?.let { toggle(it.app.packageName, checked) }
            }
            if (iconChanged) { clearIcon(); if (active && itemView.isAttachedToWindow) loadIcon() }
        }
        fun loadIcon() {
            iconJob?.cancel()
            val data = row?.app ?: return
            if (!data.installed) return
            iconJob = scope.launch {
                val bitmap = icons.load(data.packageName)
                if (row?.app?.packageName == data.packageName && active && bitmap != null) binding.notificationAppIcon.setImageBitmap(bitmap)
            }
        }
        fun pauseIcon() { iconJob?.cancel(); iconJob = null }
        fun clearIcon() { pauseIcon(); binding.notificationAppIcon.setImageResource(android.R.drawable.sym_def_app_icon) }
    }
    private class EmptyHolder(val binding: ItemNotificationEmptyBinding) : RecyclerView.ViewHolder(binding.root)
    companion object { private const val SELECTION = "selection" }
}
