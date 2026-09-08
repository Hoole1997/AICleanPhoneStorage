package com.example.aicleanphonestorage.feature.appmanager.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ItemAppManagerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 只为可见条目请求图标；后台/复用时取消请求，避免错位回填和持有不可见页面。 */
internal class AppManagerAdapter(
    private val scope: CoroutineScope,
    private val icons: AppIconLoader,
    private val open: (String) -> Unit,
) : ListAdapter<InstalledAppSummary, AppManagerAdapter.Holder>(DIFF) {
    private var active = false
    private val attached = mutableSetOf<Holder>()

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAppManagerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewAttachedToWindow(holder: Holder) {
        attached += holder
        if (active) holder.loadIcon()
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        attached -= holder
        holder.clearIcon()
    }

    override fun onViewRecycled(holder: Holder) {
        attached -= holder
        holder.recycle()
    }

    fun setActive(value: Boolean) {
        active = value
        attached.forEach { if (value) it.loadIcon() else it.clearIcon() }
        if (!value) icons.clear()
    }

    inner class Holder(private val binding: ItemAppManagerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var row: InstalledAppSummary? = null
        private var request: Job? = null
        private var renderedPackage: String? = null

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) open(getItem(position).packageName)
            }
            ViewCompat.setScreenReaderFocusable(binding.root, true)
        }

        fun bind(value: InstalledAppSummary) {
            if (row?.packageName != value.packageName) clearIcon()
            row = value
            binding.appManagerName.text = value.label
            binding.root.contentDescription =
                itemView.context.getString(R.string.app_manager_open_app, value.label)
            if (active && itemView.isAttachedToWindow) loadIcon()
        }

        fun loadIcon() {
            val value = row ?: return
            if (request?.isActive == true || renderedPackage == value.packageName) return
            request =
                scope.launch {
                    val bitmap = icons.load(value.packageName)
                    if (active && row?.packageName == value.packageName && bitmap != null) {
                        binding.appManagerIcon.setImageBitmap(bitmap)
                        renderedPackage = value.packageName
                    }
                }
        }

        fun clearIcon() {
            request?.cancel()
            request = null
            renderedPackage = null
            binding.appManagerIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        fun recycle() {
            clearIcon()
            row = null
        }
    }

    companion object {
        val DIFF =
            object : DiffUtil.ItemCallback<InstalledAppSummary>() {
                override fun areItemsTheSame(old: InstalledAppSummary, new: InstalledAppSummary) =
                    old.packageName == new.packageName

                override fun areContentsTheSame(
                    old: InstalledAppSummary,
                    new: InstalledAppSummary,
                ) = old == new
            }
    }
}
