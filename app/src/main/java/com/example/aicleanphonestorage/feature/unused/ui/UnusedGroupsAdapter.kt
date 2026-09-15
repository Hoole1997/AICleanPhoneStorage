package com.example.aicleanphonestorage.feature.unused.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemJunkCategoryBinding
import com.example.aicleanphonestorage.feature.unused.data.*

internal val UnusedKind.titleRes: Int get() = when (this) {
    UnusedKind.INSTALLED_APK -> R.string.unused_installed_apk
    UnusedKind.RESIDUE -> R.string.unused_residue
    UnusedKind.DOWNLOAD -> R.string.unused_download
}

/** 三条固定汇总复用现有分类卡片；文件明细仍走共享 Paging，不把文件列表装入状态。 */
internal class UnusedGroupsAdapter(
    private val open: (UnusedKind) -> Unit,
    private val select: (String, Boolean) -> Unit,
) : ListAdapter<Pair<UnusedGroup, Boolean>, UnusedGroupsAdapter.Holder>(DIFF) {
    fun submit(groups: List<UnusedGroup>, enabled: Boolean) = submitList(groups.map { it to enabled })
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemJunkCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemJunkCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Pair<UnusedGroup, Boolean>) {
            val (group, enabled) = row
            val totals = group.totals
            val context = binding.root.context
            val checked = totals.count > 0 && totals.count == totals.selectedCount
            binding.junkCategoryTitle.setText(group.kind.titleRes)
            binding.junkCategorySize.text = Formatter.formatShortFileSize(context, totals.bytes)
            binding.junkCategoryCount.text = java.text.NumberFormat.getIntegerInstance().format(totals.count)
            binding.junkCategoryCheck.isSelected = checked
            binding.junkCategoryCheck.isEnabled = enabled && totals.count > 0
            binding.junkCategoryCheck.contentDescription = context.getString(R.string.junk_select_category,
                context.getString(group.kind.titleRes), totals.selectedCount, totals.count)
            binding.junkCategoryCheck.setOnClickListener { select(group.kind.bucket, !checked) }
            binding.junkCategoryBody.isEnabled = enabled
            binding.junkCategoryBody.setOnClickListener { open(group.kind) }
        }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pair<UnusedGroup, Boolean>>() {
            override fun areItemsTheSame(a: Pair<UnusedGroup, Boolean>, b: Pair<UnusedGroup, Boolean>) = a.first.kind == b.first.kind
            override fun areContentsTheSame(a: Pair<UnusedGroup, Boolean>, b: Pair<UnusedGroup, Boolean>) = a == b
        }
    }
}
