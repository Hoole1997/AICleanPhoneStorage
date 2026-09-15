package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemCleanupFilterBinding
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*

/** 筛选器只修改查询条件，复用同一索引，不重新扫描设备。 */
internal class CleanupFilters(
    private val binding: ScreenFileCleanupBinding,
    private val update: (CleanupFilter) -> Unit,
) {
    private var filter = CleanupFilter()
    private var popup: ListPopupWindow? = null
    private val context
        get() = binding.root.context

    private val categories =
        intArrayOf(
            R.string.cleanup_all_types,
            R.string.cleanup_photos,
            R.string.cleanup_videos,
            R.string.cleanup_audio,
            R.string.cleanup_documents,
            R.string.cleanup_archives,
            R.string.cleanup_apk,
            R.string.cleanup_other,
        )

    init {
        binding.cleanupType.setOnClickListener {
            menu(
                binding.cleanupFilters,
                categories.map(context::getString),
                filter.category.ordinal,
            ) {
                update(filter.copy(category = FileCategory.entries[it]))
            }
        }
        binding.cleanupSize.setOnClickListener {
            val sizes = listOf(10L, 50L, 100L, 500L, 1000L)
            menu(
                binding.cleanupFilters,
                sizes.map(::sizeLabel),
                sizes.indexOf(filter.minimumBytes / 1_000_000),
            ) {
                update(filter.copy(minimumBytes = sizes[it] * 1_000_000))
            }
        }
        binding.cleanupAge.setOnClickListener {
            val days = listOf(0, 7, 30, 90, 180, 365)
            menu(
                binding.cleanupFilters,
                days.map(::timeLabel),
                days.indexOf(filter.recentDays),
            ) {
                update(filter.copy(recentDays = days[it]))
            }
        }
        binding.cleanupUnusedAge.setOnClickListener {
            val days = listOf(30, 90, 180)
            menu(
                binding.cleanupUnusedAge,
                days.map { context.getString(R.string.cleanup_unchanged_days, it) },
                days.indexOf(filter.unusedDays),
            ) {
                update(filter.copy(unusedDays = days[it]))
            }
        }
    }

    fun render(value: CleanupFilter, enabled: Boolean) {
        filter = value
        binding.cleanupType.text = context.getString(categories[value.category.ordinal])
        binding.cleanupSize.text = sizeLabel(value.minimumBytes / 1_000_000)
        binding.cleanupAge.text = timeLabel(value.recentDays)
        binding.cleanupUnusedAge.text =
            context.getString(R.string.cleanup_unchanged_days, value.unusedDays)
        listOf(
                binding.cleanupType,
                binding.cleanupSize,
                binding.cleanupAge,
                binding.cleanupUnusedAge,
            )
            .forEach { it.isEnabled = enabled }
    }

    private fun sizeLabel(megabytes: Long) = if (megabytes == 1000L) context.getString(R.string.cleanup_one_gb)
        else context.getString(R.string.cleanup_size_mb, megabytes)

    private fun timeLabel(days: Int) = context.getString(when (days) {
        7 -> R.string.cleanup_one_week
        30 -> R.string.cleanup_one_month
        90 -> R.string.cleanup_three_months
        180 -> R.string.cleanup_six_months
        365 -> R.string.cleanup_one_year
        else -> R.string.cleanup_all_time
    })

    private fun menu(anchor: View, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        popup?.dismiss()
        popup =
            ListPopupWindow(context).apply {
                anchorView = anchor
                width = anchor.width
                isModal = true
                setBackgroundDrawable(
                    androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        context,
                        R.drawable.cleanup_confirm_surface,
                    )
                )
                setAdapter(
                    object : BaseAdapter() {
                        override fun getCount() = labels.size

                        override fun getItem(position: Int) = labels[position]

                        override fun getItemId(position: Int) = position.toLong()

                        override fun getView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup,
                        ): View {
                            val row =
                                convertView?.let(ItemCleanupFilterBinding::bind)
                                    ?: ItemCleanupFilterBinding.inflate(
                                        android.view.LayoutInflater.from(context),
                                        parent,
                                        false,
                                    )
                            row.filterLabel.text = labels[position]
                            row.filterChecked.isVisible = position == selected
                            return row.root
                        }
                    }
                )
                setOnItemClickListener { _, _, position, _ ->
                    dismiss()
                    onSelect(position)
                }
                show()
            }
    }

    fun close() {
        popup?.dismiss()
        popup = null
    }
}
