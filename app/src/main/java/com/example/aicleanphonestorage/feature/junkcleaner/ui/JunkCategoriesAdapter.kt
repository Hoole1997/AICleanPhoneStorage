package com.example.aicleanphonestorage.feature.junkcleaner.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemJunkCategoryBinding
import com.example.aicleanphonestorage.databinding.ItemJunkOverviewBinding
import com.example.aicleanphonestorage.databinding.ItemJunkSectionBinding
import com.example.aicleanphonestorage.feature.junkcleaner.data.*

internal sealed interface JunkRow {
    data class Overview(val bytes: Long, val used: Long, val total: Long) : JunkRow

    data class Section(val photos: Boolean) : JunkRow

    data class Category(val value: JunkCategorySummary, val enabled: Boolean) : JunkRow
}

internal class JunkCategoriesAdapter(
    private val open: (JunkKind) -> Unit,
    private val select: (JunkKind, Boolean) -> Unit,
) : ListAdapter<JunkRow, RecyclerView.ViewHolder>(DIFF) {
    fun submit(snapshot: JunkSnapshot, enabled: Boolean) {
        val visible = JunkKind.visible.mapNotNull { kind -> snapshot.categories.find { it.kind == kind } }
        submitList(
            buildList {
                add(
                    JunkRow.Overview(
                        visible.sumOf { it.bytes },
                        snapshot.storage.usedBytes,
                        snapshot.storage.totalBytes,
                    )
                )
                add(JunkRow.Section(false))
                visible.forEach { add(JunkRow.Category(it, enabled)) }
            }
        )
    }

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is JunkRow.Section -> 0
            is JunkRow.Category -> 1
            is JunkRow.Overview -> 2
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 2) Overview(ItemJunkOverviewBinding.inflate(inflater, parent, false))
        else if (viewType == 0) Section(ItemJunkSectionBinding.inflate(inflater, parent, false))
        else Category(ItemJunkCategoryBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is JunkRow.Overview -> (holder as Overview).bind(row)
            is JunkRow.Section -> (holder as Section).bind(row.photos)
            is JunkRow.Category -> (holder as Category).bind(row)
        }
    }

    private class Overview(private val binding: ItemJunkOverviewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: JunkRow.Overview) {
            val context = binding.root.context
            val formatted = Formatter.formatShortFileSize(context, row.bytes)
            val split =
                formatted.indexOfLast { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
            binding.junkBytes.text = if (split > 0) formatted.substring(0, split) else formatted
            binding.junkUnit.text = if (split > 0) formatted.substring(split + 1) else ""
            binding.junkStorage.text =
                context.getString(
                    R.string.junk_storage,
                    Formatter.formatShortFileSize(context, row.used),
                    Formatter.formatShortFileSize(context, row.total),
                )
        }
    }

    private class Section(private val binding: ItemJunkSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            ViewCompat.setAccessibilityHeading(binding.junkSectionTitle, true)
        }

        fun bind(photos: Boolean) {
            binding.junkSectionTitle.setText(
                if (photos) R.string.junk_photos_section else R.string.junk_files_section
            )
        }
    }

    private inner class Category(private val binding: ItemJunkCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var row: JunkRow.Category? = null

        init {
            binding.junkCategoryBody.setOnClickListener {
                row?.takeIf { it.enabled }?.let { open(it.value.kind) }
            }
            binding.junkCategoryCheck.setOnClickListener {
                row?.takeIf { it.enabled }
                    ?.let { select(it.value.kind, it.value.selected != it.value.count) }
            }
            ViewCompat.setAccessibilityDelegate(
                binding.junkCategoryCheck,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: android.view.View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.CheckBox"
                        info.isCheckable = true
                        info.isChecked =
                            row?.value?.let { it.count > 0 && it.selected == it.count } == true
                    }
                },
            )
        }

        fun bind(value: JunkRow.Category) {
            row = value
            val data = value.value
            val context = binding.root.context
            binding.junkCategoryTitle.setText(data.kind.titleRes)
            binding.junkCategorySize.text = Formatter.formatShortFileSize(context, data.bytes)
            binding.junkCategoryCount.text =
                java.text.NumberFormat.getIntegerInstance().format(data.count)
            binding.junkCategoryCheck.isSelected = data.count > 0 && data.count == data.selected
            binding.junkCategoryCheck.isEnabled = value.enabled && data.count > 0
            binding.junkCategoryCheck.contentDescription =
                context.getString(
                    R.string.junk_select_category,
                    context.getString(data.kind.titleRes),
                    data.selected,
                    data.count,
                )
            binding.junkCategoryBody.isEnabled = value.enabled
            binding.junkCategoryBody.contentDescription =
                context.getString(data.kind.titleRes) +
                    ", " +
                    context.getString(data.kind.descriptionRes)
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<JunkRow>() {
                override fun areItemsTheSame(a: JunkRow, b: JunkRow) =
                    when {
                        a is JunkRow.Overview && b is JunkRow.Overview -> true
                        a is JunkRow.Section && b is JunkRow.Section -> a.photos == b.photos
                        a is JunkRow.Category && b is JunkRow.Category ->
                            a.value.kind == b.value.kind
                        else -> false
                    }

                override fun areContentsTheSame(a: JunkRow, b: JunkRow) = a == b
            }
    }
}
