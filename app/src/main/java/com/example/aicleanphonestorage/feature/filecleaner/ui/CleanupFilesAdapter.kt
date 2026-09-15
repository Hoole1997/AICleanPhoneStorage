package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.graphics.Rect
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemCleanupFileBinding
import com.example.aicleanphonestorage.databinding.ItemCleanupPhotoBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

/** Paging 限制驻留条目；勾选/质量变化只更新 payload，不重置图片或滚动位置。 */
internal class CleanupFilesAdapter(
    private val feature: CleanupFeature,
    private val loader: CleanupThumbnailLoader,
    private val scope: CoroutineScope,
    private val toggle: (Long, Boolean) -> Unit,
    private val preview: (ScannedFile) -> Unit,
    private val quality: (ScannedFile) -> Unit,
    private val photoGrid: Boolean = false,
) : PagingDataAdapter<ScannedFile, CleanupFilesAdapter.Holder>(DIFF) {
    private val grid =
        photoGrid ||
            feature == CleanupFeature.PHOTO_COMPRESS ||
            feature == CleanupFeature.SCREENSHOTS
    private val date = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
    private val holders = mutableSetOf<Holder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(parent.context)
        return if (grid) PhotoHolder(ItemCleanupPhotoBinding.inflate(inflater, parent, false))
        else FileHolder(ItemCleanupFileBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position), false)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) =
        holder.bind(getItem(position), payloads.isNotEmpty())

    override fun onViewAttachedToWindow(holder: Holder) {
        holders += holder
        holder.startImage()
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        holders -= holder
        holder.stopImage()
    }

    override fun onViewRecycled(holder: Holder) {
        holders -= holder
        holder.clear()
    }

    fun pause() {
        holders.forEach { it.stopImage() }
        loader.clear()
    }

    fun resume() {
        holders.forEach { it.startImage() }
    }

    abstract inner class Holder(view: View, private val image: ImageView, private val check: View) :
        RecyclerView.ViewHolder(view) {
        var row: ScannedFile? = null
        private var imageJob: Job? = null

        init {
            check.setOnClickListener {
                row?.let { if (it.retained) preview(it) else toggle(it.id, !it.selected) }
            }
            ViewCompat.setAccessibilityDelegate(
                check,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.CheckBox"
                        info.isCheckable = true
                        info.isChecked = row?.selected == true
                    }
                },
            )
        }

        fun bind(file: ScannedFile?, payload: Boolean) {
            val changed = row?.id != file?.id || row?.modifiedMillis != file?.modifiedMillis
            row = file
            check.isSelected = file?.selected == true
            check.isEnabled = file?.retained != true
            check.isVisible = file?.retained != true
            check.contentDescription =
                file?.let { itemView.context.getString(R.string.cleanup_select_file, it.name) }
            if (changed || !payload) {
                stopImage()
                image.setImageResource(
                    if (feature == CleanupFeature.SMART_CLEAN) R.drawable.junk_file
                    else R.drawable.ic_tool_large_files
                )
                startImage()
            }
            if (file != null) bindText(file)
        }

        abstract fun bindText(file: ScannedFile)

        fun startImage() {
            val file = row ?: return
            if (imageJob?.isActive == true) return
            imageJob =
                scope.launch {
                    val bitmap = loader.load(file, if (grid) 384 else 128)
                    if (row?.id == file.id && bitmap != null) image.setImageBitmap(bitmap)
                }
        }

        fun stopImage() {
            imageJob?.cancel()
            imageJob = null
        }

        fun clear() {
            stopImage()
            row = null
            image.setImageDrawable(null)
        }
    }

    inner class FileHolder(private val binding: ItemCleanupFileBinding) :
        Holder(binding.root, binding.fileIcon, binding.fileCheck) {
        init {
            binding.root.setOnClickListener {
                row?.let { if (it.retained) preview(it) else toggle(it.id, !it.selected) }
            }
            binding.fileIcon.setOnClickListener {
                row?.let { if (it.isDirectory) toggle(it.id, !it.selected) else preview(it) }
            }
        }

        override fun bindText(file: ScannedFile) {
            binding.fileName.text = file.name
            binding.fileDetail.text =
                if (feature == CleanupFeature.UNUSED_FILES) date.format(Date(file.modifiedMillis))
                else Formatter.formatShortFileSize(binding.root.context, file.size)
        }
    }

    inner class PhotoHolder(private val binding: ItemCleanupPhotoBinding) :
        Holder(binding.root, binding.photoImage, binding.photoCheck) {
        init {
            binding.root.setOnClickListener {
                row?.let { if (it.retained) preview(it) else toggle(it.id, !it.selected) }
            }
            binding.photoExpand.setOnClickListener { row?.let(preview) }
            binding.photoSavingAction.setOnClickListener { row?.let(quality) }
            binding.photoShade.isVisible = feature == CleanupFeature.PHOTO_COMPRESS
            binding.photoSavingAction.isVisible = feature == CleanupFeature.PHOTO_COMPRESS
            binding.photoExpand.isVisible = feature == CleanupFeature.PHOTO_COMPRESS
            binding.root.setOnLongClickListener {
                row?.let(preview)
                true
            }
        }

        override fun bindText(file: ScannedFile) {
            val context = binding.root.context
            binding.photoKeep.isVisible = file.retained
            binding.photoExpand.contentDescription =
                context.getString(R.string.cleanup_preview, file.name)
            // 徽标说明实际编码质量；质量 75 不代表体积减少 25%。
            binding.photoSaving.text = QualityOption.entries.firstOrNull { it.value == file.quality }
                ?.let { context.getString(it.title) }
                ?: (context.getString(R.string.cleanup_quality) + " " + file.quality)
            binding.photoSavingAction.contentDescription =
                context.getString(R.string.cleanup_quality) + ", " + file.name + ", " + file.quality
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<ScannedFile>() {
                override fun areItemsTheSame(old: ScannedFile, new: ScannedFile) = old.id == new.id

                override fun areContentsTheSame(old: ScannedFile, new: ScannedFile) = old == new

                override fun getChangePayload(old: ScannedFile, new: ScannedFile): Any? =
                    if (old.copy(selected = new.selected, quality = new.quality) == new) true
                    else null
            }
    }
}

internal class CleanupGridSpacing(private val columns: Int, private val gap: Int) :
    RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % columns
        outRect.set(column * gap / columns, 0, gap - (column + 1) * gap / columns, gap)
    }
}
