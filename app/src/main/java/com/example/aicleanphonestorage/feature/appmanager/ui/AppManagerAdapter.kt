package com.example.aicleanphonestorage.feature.appmanager.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ItemAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.ManagedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 只为可见条目请求图标；后台/复用时取消请求，避免错位回填和持有不可见页面。 */
internal class AppManagerAdapter(
    private val scope: CoroutineScope,
    private val icons: AppIconLoader,
    private val open: (String) -> Unit,
    private val uninstall: (ManagedApp) -> Unit,
) : ListAdapter<ManagedApp, AppManagerAdapter.Holder>(DIFF) {
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
        private var row: ManagedApp? = null
        private var request: Job? = null
        private var renderedPackage: String? = null
        private val presentation = AppManagerPresentation(itemView.context)

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) open(getItem(position).packageName)
            }
            binding.appManagerUninstall.setOnClickListener {
                val value = row ?: return@setOnClickListener
                uninstall(value) // 同一按钮统一上报，系统不可卸载项由 action 层回退详情页。
            }
            ViewCompat.setScreenReaderFocusable(binding.root, true)
        }

        fun bind(value: ManagedApp) {
            if (row?.packageName != value.packageName) clearIcon()
            row = value
            binding.appManagerName.text = value.label
            binding.appManagerInstalled.text = presentation.installed(value)
            binding.appManagerSize.text = presentation.size(value)
            binding.appManagerUsed.text = presentation.used(value)
            binding.appManagerUninstall.setText(R.string.app_manager_uninstall)
            binding.appManagerUninstall.contentDescription =
                itemView.context.getString(
                    if (value.canUninstall) R.string.app_manager_uninstall_app
                    else R.string.app_manager_open_app,
                    value.label,
                )
            binding.root.contentDescription =
                listOf(
                        value.label,
                        binding.appManagerInstalled.text,
                        binding.appManagerSize.text,
                        binding.appManagerUsed.text,
                        itemView.context.getString(R.string.app_manager_open_app, value.label),
                    )
                    .joinToString(". ")
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
            object : DiffUtil.ItemCallback<ManagedApp>() {
                override fun areItemsTheSame(old: ManagedApp, new: ManagedApp) =
                    old.packageName == new.packageName

                override fun areContentsTheSame(old: ManagedApp, new: ManagedApp) = old == new
            }
    }
}
