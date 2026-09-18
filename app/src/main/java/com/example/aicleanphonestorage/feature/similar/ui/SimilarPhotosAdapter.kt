package com.example.aicleanphonestorage.feature.similar.ui

import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.core.view.*
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemSimilarHeaderBinding
import com.example.aicleanphonestorage.databinding.ItemSimilarPhotoBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.ScannedFile
import com.example.aicleanphonestorage.feature.filecleaner.ui.CleanupThumbnailLoader
import com.example.aicleanphonestorage.feature.similar.data.SimilarRow
import kotlinx.coroutines.*

/** 同一 RecyclerView 中拼接卡片标题、小行和圆角底部；只对可见小行加载图片，不嵌套滚动。 */
internal class SimilarPhotosAdapter(
    private val columns: Int,
    private val loader: CleanupThumbnailLoader,
    private val scope: CoroutineScope,
    private val toggle: (Long, Boolean) -> Unit,
    private val group: (String, Boolean) -> Unit,
    private val original: (Long) -> Unit,
    private val unavailable: (Long) -> Unit,
    private val preview: (ScannedFile) -> Unit,
) : PagingDataAdapter<SimilarRow, RecyclerView.ViewHolder>(DIFF) {
    private val visible = mutableSetOf<Photos>()
    private var active = false

    override fun getItemViewType(position: Int) =
        when (peek(position)) {
            is SimilarRow.Header -> 0
            is SimilarRow.Photos -> 1
            else -> 2
        }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (type) {
            0 -> Header(ItemSimilarHeaderBinding.inflate(inflater, parent, false))
            1 ->
                Photos(
                    LinearLayout(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(-1, -2)
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundColor(Color.WHITE)
                        setPadding(dp(12), 0, dp(12), dp(10))
                    }
                )
            else ->
                object :
                    RecyclerView.ViewHolder(
                        FrameLayout(parent.context).apply {
                            layoutParams = RecyclerView.LayoutParams(-1, dp(18))
                            addView(
                                View(context).apply {
                                    setBackgroundResource(R.drawable.similar_group_bottom)
                                },
                                FrameLayout.LayoutParams(-1, dp(6)),
                            )
                        }
                    ) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SimilarRow.Header -> (holder as Header).bind(row)
            is SimilarRow.Photos -> (holder as Photos).bind(row)
            else -> Unit
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is Photos) {
            visible += holder
            holder.start()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is Photos) {
            visible -= holder
            holder.stop()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is Photos) {
            visible -= holder
            holder.clear()
        }
    }

    fun resume() {
        active = true
        visible.forEach { it.start() }
    }

    fun pause() {
        active = false
        visible.forEach { it.stop() }
        loader.clear()
    }

    private inner class Header(private val b: ItemSimilarHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: SimilarRow.Header) {
            val context = b.root.context
            b.similarGroupTitle.text =
                context.getString(
                    R.string.similar_group_title,
                    java.text.NumberFormat.getIntegerInstance().format(row.count),
                )
            b.similarGroupSize.text =
                context.getString(
                    R.string.similar_group_size,
                    android.text.format.Formatter.formatShortFileSize(context, row.bytes),
                )
            val selected = row.deletable > 0 && row.selected == row.deletable
            b.similarGroupCheck.isSelected = selected
            b.root.setOnClickListener { group(row.key, !selected) }
            b.root.isEnabled = row.deletable > 0
            ViewCompat.setAccessibilityHeading(b.similarGroupTitle, true)
            check(b.root) { selected }
        }
    }

    private inner class Photos(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        private val cells =
            List(columns) { i ->
                val binding =
                    ItemSimilarPhotoBinding.inflate(LayoutInflater.from(row.context), row, false)
                row.addView(
                    binding.root,
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (i > 0) marginStart = row.dp(10)
                    },
                )
                Cell(binding)
            }

        fun bind(value: SimilarRow.Photos) {
            cells.forEachIndexed { i, c -> c.bind(value.files.getOrNull(i)) }
        }

        fun start() {
            cells.forEach { it.start() }
        }

        fun stop() {
            cells.forEach { it.stop() }
        }

        fun clear() {
            cells.forEach { it.bind(null) }
        }
    }

    private inner class Cell(private val b: ItemSimilarPhotoBinding) {
        private var file: ScannedFile? = null
        private var job: Job? = null
        private var loaded: Long? = null

        init {
            b.root.clipToOutline = true
            b.similarCheck.setOnClickListener {
                file?.takeIf { it.available && !it.retained }?.let { toggle(it.id, !it.selected) }
            }
            b.root.setOnClickListener {
                file
                    ?.takeIf { it.available }
                    ?.let {
                        when {
                            it.retained -> preview(it)
                            !it.selected -> original(it.id)
                            else -> preview(it)
                        }
                    }
            }
            b.root.setOnLongClickListener {
                file?.takeIf { it.available }?.let(preview)
                true
            }
            check(b.similarCheck) { file?.selected == true }
        }

        fun bind(value: ScannedFile?) {
            if (
                file?.id != value?.id ||
                    file?.modifiedMillis != value?.modifiedMillis ||
                    file?.available != value?.available
            )
                stop()
            file = value
            b.root.visibility = if (value == null) View.INVISIBLE else View.VISIBLE
            b.similarOriginal.isVisible = value?.retained == true
            b.similarCheck.isVisible = value?.retained != true
            b.similarCheck.isEnabled = value?.available == true
            b.similarCheck.isSelected = value?.selected == true
            b.root.alpha = if (value?.available == false) 0.5f else 1f
            b.similarSize.text =
                value
                    ?.let {
                        android.text.format.Formatter.formatShortFileSize(b.root.context, it.size)
                    }
                    .orEmpty()
            b.similarCheck.contentDescription = value?.let {
                b.root.context.getString(R.string.cleanup_select_file, it.name)
            }
            b.root.contentDescription = value?.let {
                when {
                    it.retained -> b.root.context.getString(R.string.similar_original_reason)
                    !it.selected ->
                        b.root.context.getString(R.string.similar_make_original, it.name)
                    else -> b.root.context.getString(R.string.cleanup_preview, it.name)
                }
            }
            start()
        }

        fun start() {
            val value = file?.takeIf { it.available } ?: return
            if (!active || loaded == value.id || job?.isActive == true) return
            job = scope.launch {
                val bitmap = loader.load(value, 320)
                ensureActive()
                if (file?.id != value.id) return@launch
                if (bitmap != null) {
                    b.similarImage.scaleType = ImageView.ScaleType.CENTER_CROP
                    b.similarImage.setImageBitmap(bitmap)
                    loaded = value.id
                } else unavailable(value.id)
            }
        }

        fun stop() {
            job?.cancel()
            job = null
            loaded = null
            b.similarImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
            b.similarImage.setImageResource(R.drawable.ic_tool_similar)
        }
    }

    companion object {
        private fun View.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        private val DIFF =
            object : DiffUtil.ItemCallback<SimilarRow>() {
                override fun areItemsTheSame(oldItem: SimilarRow, newItem: SimilarRow) =
                    oldItem::class == newItem::class && oldItem.key == newItem.key

                override fun areContentsTheSame(oldItem: SimilarRow, newItem: SimilarRow) =
                    oldItem == newItem
            }

        private fun check(view: View, checked: () -> Boolean) {
            ViewCompat.setAccessibilityDelegate(
                view,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.CheckBox"
                        info.isCheckable = true
                        info.isChecked = checked()
                    }
                },
            )
        }
    }
}
