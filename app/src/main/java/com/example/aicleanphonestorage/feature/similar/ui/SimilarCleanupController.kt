package com.example.aicleanphonestorage.feature.similar.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.ad.*
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 分组布局的生命周期接线集中在本组件，共享 Activity 保持导航/系统授权职责。 */
internal class SimilarCleanupController(
    activity: AppCompatActivity,
    private val binding: ScreenFileCleanupBinding,
    model: CleanupViewModel,
    executor: TaskExecutor,
    listState: CleanupListStateRenderer,
    preview: (ScannedFile) -> Unit,
) {
    private val columns = if (activity.resources.configuration.fontScale >= 1.5f) 2 else 3
    private val adapter =
        SimilarPhotosAdapter(
            columns,
            CleanupThumbnailLoader(activity, executor),
            activity.lifecycleScope,
            model::toggle,
            model::selectBucket,
            model::makeOriginal,
            model::photoUnavailable,
            preview,
        )

    init {
        model.setSimilarColumns(columns)
        NativeAdCoordinator(activity, binding.nativeAd, NativeAdFeature.DUPLICATE.featureSlot)
        binding.cleanupFiles.layoutManager = LinearLayoutManager(activity)
        binding.cleanupFiles.itemAnimator = null
        binding.cleanupFiles.setPadding(
            binding.cleanupFiles.paddingLeft,
            (14 * activity.resources.displayMetrics.density).toInt(),
            binding.cleanupFiles.paddingRight,
            binding.cleanupFiles.paddingBottom,
        )
        binding.cleanupFiles.adapter = adapter
        adapter.addLoadStateListener { listState.loading(it, adapter.itemCount) }
        adapter.addOnPagesUpdatedListener { listState.pagesPresented(adapter.itemCount) }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.similarRows.collectLatest(adapter::submitData)
            }
        }
        adapter.resume()
    }

    fun render(state: CleanupUiState) {
        val context = binding.root.context
        val handle = state.handle ?: return
        binding.cleanupSelectionSummary.isVisible = state.totalsReady
        binding.cleanupSelectionSummary.text =
            context.getString(
                R.string.similar_selected,
                java.text.NumberFormat.getIntegerInstance().format(state.totals.selectedCount),
                android.text.format.Formatter.formatShortFileSize(
                    context,
                    state.totals.selectedBytes,
                ),
            )
        val notes = buildList {
            if (handle.partial) add(context.getString(R.string.cleanup_limited))
            if (handle.analysisSkipped > 0)
                add(context.getString(R.string.similar_skipped, handle.analysisSkipped))
        }
        binding.cleanupScope.isVisible = notes.isNotEmpty()
        binding.cleanupScope.text = notes.joinToString("\n")
    }

    fun retry() = adapter.retry()

    fun resume() = adapter.resume()

    fun pause() = adapter.pause()
}
