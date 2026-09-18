package com.example.aicleanphonestorage.feature.videos.ui

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemVideoBinding
import com.example.aicleanphonestorage.databinding.ItemVideoMonthBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import com.example.aicleanphonestorage.feature.videos.data.VideoRow
import kotlinx.coroutines.*

/** 单一 RecyclerView 的跨列月份标题和三列视频；只持有可见 holder，停止后释放位图引用。 */
internal class VideoFilesAdapter(
    private val loader: VideoThumbnailLoader,
    private val scope: CoroutineScope,
    private val toggle: (Long, Boolean) -> Unit,
    private val selectMonth: (String, Boolean) -> Unit,
    private val collapse: (String, Boolean) -> Unit,
    private val preview: (ScannedFile) -> Unit,
) : PagingDataAdapter<VideoRow, RecyclerView.ViewHolder>(DIFF) {
    private var active = false
    private val visible = mutableSetOf<VideoHolder>()
    fun isHeader(position: Int) = peek(position) is VideoRow.Month
    override fun getItemViewType(position: Int) = if (isHeader(position)) 0 else 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) MonthHolder(ItemVideoMonthBinding.inflate(inflater, parent, false))
        else VideoHolder(ItemVideoBinding.inflate(inflater, parent, false))
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MonthHolder -> (getItem(position) as? VideoRow.Month)?.let(holder::bind)
            is VideoHolder -> holder.bind((getItem(position) as? VideoRow.Video)?.file)
        }
    }
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is VideoHolder) { visible += holder; holder.start() }
    }
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is VideoHolder) { visible -= holder; holder.stop() }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is VideoHolder) { visible -= holder; holder.stop(); holder.file = null }
    }
    fun resume() { active = true; visible.forEach { it.start() } }
    fun pause() { active = false; visible.forEach { it.stop() }; loader.clear() }

    private inner class MonthHolder(private val binding: ItemVideoMonthBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: VideoRow.Month) {
            val context = binding.root.context
            ViewCompat.setAccessibilityHeading(binding.videoMonth, true)
            binding.videoMonth.text = row.month
            binding.videoMonthSummary.text = context.resources.getQuantityString(R.plurals.video_month_summary, row.count,
                java.text.NumberFormat.getIntegerInstance().format(row.count),
                com.example.aicleanphonestorage.core.format.StorageSizeFormatter(context).megabytes(row.bytes))
            binding.videoMonthAll.isSelected = row.selected == row.count
            binding.videoMonthAll.contentDescription = context.getString(R.string.video_select_month, row.month)
            checkbox(binding.videoMonthAll) { row.selected == row.count }
            binding.videoMonthAll.setOnClickListener { selectMonth(row.month, row.selected != row.count) }
            binding.videoMonthExpand.rotation = if (row.collapsed) 180f else 0f
            binding.videoMonthExpand.contentDescription = context.getString(R.string.video_toggle_month, row.month)
            binding.videoMonthExpand.setOnClickListener { collapse(row.month, !row.collapsed) }
        }
    }
    private inner class VideoHolder(private val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        var file: ScannedFile? = null
        private var job: Job? = null
        private var displayed: Long? = null
        init {
            // XML 的 clipToOutline 仅在新平台生效，属性调用兼容项目 minSdk 26。
            binding.root.clipToOutline = true
            binding.root.setOnClickListener { file?.let { toggle(it.id, !it.selected) } }
            binding.videoCheck.setOnClickListener { file?.let { toggle(it.id, !it.selected) } }
            binding.videoPlay.setOnClickListener { file?.let(preview) }
            checkbox(binding.videoCheck) { file?.selected == true }
        }
        fun bind(value: ScannedFile?) {
            if (file?.id != value?.id || file?.modifiedMillis != value?.modifiedMillis) stop()
            file = value
            binding.videoCheck.isSelected = value?.selected == true
            binding.videoCheck.contentDescription = value?.let { itemView.context.getString(R.string.cleanup_select_file, it.name) }
            binding.videoPlay.contentDescription = value?.let { itemView.context.getString(R.string.cleanup_preview, it.name) }
            binding.videoSize.text = value?.let { android.text.format.Formatter.formatShortFileSize(itemView.context, it.size) }.orEmpty()
            start()
        }
        fun start() {
            val value = file ?: return
            if (!active || job?.isActive == true || displayed == value.id) return
            job = scope.launch {
                try {
                    val bitmap = loader.load(value)
                    ensureActive()
                    if (file?.id == value.id && active) {
                        if (bitmap != null) {
                            binding.videoImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            binding.videoImage.setImageBitmap(bitmap)
                        }
                        displayed = value.id
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: RuntimeException) { /* Provider 解码失败保持原版视频占位，不影响选择。 */ }
            }
        }
        fun stop() {
            job?.cancel(); job = null; displayed = null
            binding.videoImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            binding.videoImage.setImageResource(R.drawable.ic_tool_videos)
        }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoRow>() {
            override fun areItemsTheSame(oldItem: VideoRow, newItem: VideoRow) = when {
                oldItem is VideoRow.Month && newItem is VideoRow.Month -> oldItem.month == newItem.month
                oldItem is VideoRow.Video && newItem is VideoRow.Video -> oldItem.file.id == newItem.file.id
                else -> false
            }
            override fun areContentsTheSame(oldItem: VideoRow, newItem: VideoRow) = oldItem == newItem
        }
        private fun checkbox(view: View, checked: () -> Boolean) {
            ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.CheckBox"; info.isCheckable = true; info.isChecked = checked()
                }
            })
        }
    }
}

internal class VideoGridSpacing(private val gap: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val params = view.layoutParams as? GridLayoutManager.LayoutParams ?: return
        val columns = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: return
        if (params.spanSize == columns) { outRect.set(0, 0, 0, 0); return }
        val column = params.spanIndex
        outRect.set(column * gap / columns, 0, gap - (column + 1) * gap / columns, gap)
    }
}
