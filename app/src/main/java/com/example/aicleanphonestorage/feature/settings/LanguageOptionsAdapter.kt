package com.example.aicleanphonestorage.feature.settings

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemLanguageOptionBinding

internal data class LanguageRow(
    val tag: String,
    val name: String,
    val detail: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/** 只更新新旧选中项，复用行；原生 RadioButton 提供可访问的单选状态。 */
internal class LanguageOptionsAdapter(private val select: (String) -> Unit) :
    ListAdapter<LanguageRow, LanguageOptionsAdapter.Holder>(Diff) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(
            ItemLanguageOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemLanguageOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var row: LanguageRow? = null

        init {
            binding.languageRadio.setOnClickListener {
                row?.takeIf { it.enabled }?.let { select(it.tag) }
            }
        }

        fun bind(value: LanguageRow) {
            row = value
            val button = binding.languageRadio
            val label =
                if (value.detail == value.name || value.detail.isBlank()) value.name
                else value.name + "\n" + value.detail
            button.text =
                SpannableString(label).apply {
                    if (label.length > value.name.length) {
                        setSpan(
                            RelativeSizeSpan(0.8125f),
                            value.name.length + 1,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        setSpan(
                            ForegroundColorSpan(
                                ContextCompat.getColor(button.context, R.color.home_text_secondary)
                            ),
                            value.name.length + 1,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            button.isChecked = value.selected
            button.isEnabled = value.enabled
            val check =
                ContextCompat.getDrawable(
                    button.context,
                    if (value.selected) R.drawable.cleanup_check_on
                    else R.drawable.cleanup_check_off,
                )
            val size = (22 * button.resources.displayMetrics.density).toInt()
            check?.setBounds(0, 0, size, size)
            button.setCompoundDrawablesRelative(null, null, check, null)
        }
    }

    private object Diff : DiffUtil.ItemCallback<LanguageRow>() {
        override fun areItemsTheSame(old: LanguageRow, new: LanguageRow) = old.tag == new.tag

        override fun areContentsTheSame(old: LanguageRow, new: LanguageRow) = old == new
    }
}
