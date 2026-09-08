package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import com.example.aicleanphonestorage.feature.junkcleaner.ui.descriptionRes
import com.example.aicleanphonestorage.feature.junkcleaner.ui.titleRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 页面仅绑定分页内容和系统交互；文件读写、选择快照和操作进度由独立组件负责。 */
class FileCleanupActivity : AppCompatActivity() {
    private val container
        get() = (application as CleanApplication).container

    private val model: CleanupViewModel by viewModels {
        viewModelFactory {
            initializer {
                CleanupViewModel(
                    container.fileScanRepository,
                    container.fileOperations,
                    createSavedStateHandle(),
                    intent.getLongExtra(EXTRA_SCAN, -1),
                    initialFilter = CleanupFilter(bucket = intent.getStringExtra(EXTRA_BUCKET)),
                )
            }
        }
    }
    private lateinit var binding: ScreenFileCleanupBinding
    private lateinit var listState:CleanupListStateRenderer
    private lateinit var filters: CleanupFilters
    private var adapter: CleanupFilesAdapter? = null
    private var lastError = 0L
    private lateinit var operationCoordinator: CleanupOperationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        operationCoordinator = CleanupOperationCoordinator(this, model, savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenFileCleanupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        listState=CleanupListStateRenderer(binding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.cleanupContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        ViewCompat.setAccessibilityHeading(binding.cleanupTitle, true)
        binding.cleanupBack.setOnClickListener { finish() }
        binding.cleanupSelectAll.setOnClickListener { model.selectAll() }
        binding.cleanupAction.setOnClickListener { model.prepare() }
        binding.cleanupError.setOnClickListener { adapter?.retry() }
        filters = CleanupFilters(binding, model::setFilter)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    private fun attach(feature: CleanupFeature) {
        val junkKind =
            com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind.from(
                intent.getStringExtra(EXTRA_BUCKET)
            )
        if (adapter != null) return
        val columns =
            if (feature == CleanupFeature.SMART_CLEAN && junkKind?.photos == true) 3
            else
                when (feature) {
                    CleanupFeature.PHOTO_COMPRESS -> 2
                    CleanupFeature.SCREENSHOTS -> 3
                    else -> 1
                }
        binding.cleanupFiles.layoutManager =
            if (columns == 1) LinearLayoutManager(this) else GridLayoutManager(this, columns)
        if (columns > 1)
            binding.cleanupFiles.addItemDecoration(
                CleanupGridSpacing(
                    columns,
                    ((if (columns == 3) 6 else 12) * resources.displayMetrics.density).toInt(),
                )
            )
        (binding.cleanupFiles.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        val files =
            CleanupFilesAdapter(
                feature,
                CleanupThumbnailLoader(this, container.taskExecutor),
                lifecycleScope,
                model::toggle,
                ::preview,
                ::quality,
                photoGrid = columns > 1,
            )
        adapter = files
        binding.cleanupFiles.adapter = files
        // 两类事件都由 Paging Presenter 在主线程同步回调，避免旧 Loading 被异步收集到页面提交之后。
        files.addLoadStateListener { listState.loading(it,files.itemCount) }
        files.addOnPagesUpdatedListener { listState.pagesPresented(files.itemCount) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.files.collectLatest(files::submitData) }


            }
        }
    }

    private fun render(state: CleanupUiState) {
        val handle = state.handle
        if (handle != null) {
            attach(handle.feature)
            val junkKind =
                com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind.from(
                    intent.getStringExtra(EXTRA_BUCKET)
                )
            binding.cleanupTitle.setText(junkKind?.titleRes ?: handle.feature.titleRes)
            if (handle.feature == CleanupFeature.SMART_CLEAN) {
                binding.root.setBackgroundColor(Color.WHITE)
                binding.cleanupBackground.isVisible = false
                binding.cleanupFooter.isVisible = false
                binding.cleanupFiles.setPadding(
                    binding.cleanupFiles.paddingLeft,
                    (18 * resources.displayMetrics.density).toInt(),
                    binding.cleanupFiles.paddingRight,
                    (16 * resources.displayMetrics.density).toInt(),
                )
            }
            binding.cleanupPhotoHeader.isVisible = handle.feature == CleanupFeature.PHOTO_COMPRESS
            binding.cleanupFilters.isVisible = handle.feature == CleanupFeature.LARGE_FILES
            binding.cleanupUnusedAge.isVisible = handle.feature == CleanupFeature.UNUSED_FILES
            binding.cleanupScope.isVisible =
                handle.partial || handle.scopeLabel == "Selected folder"
            binding.cleanupScope.text =
                if (handle.partial) getString(R.string.cleanup_limited) else handle.scopeLabel
            if (handle.feature == CleanupFeature.SMART_CLEAN && junkKind != null) {
                binding.cleanupScope.isVisible = true
                binding.cleanupScope.setText(junkKind.descriptionRes)
            }
            binding.cleanupPotential.text =
                Formatter.formatShortFileSize(this, state.totals.estimatedSaving)
            binding.cleanupAction.setText(
                if (handle.feature == CleanupFeature.PHOTO_COMPRESS) R.string.cleanup_compress
                else R.string.cleanup_clean
            )
        }
        val idle = state.operation == CleanupOperationState.Idle
        binding.cleanupAction.isEnabled =
            idle && state.editing == 0 && state.totals.selectedCount > 0
        binding.cleanupSelectAll.isEnabled = idle && state.editing == 0 && state.totals.count > 0
        binding.cleanupSelectAll.setText(
            if (state.totals.selectedCount == state.totals.count && state.totals.count > 0)
                R.string.cleanup_deselect_all
            else R.string.cleanup_select_all
        )
        filters.render(state.filter, idle)
        if (state.error > lastError) {
            lastError = state.error
            toast(R.string.cleanup_action_failed)
        }
        if (handle == null && state.error > 0) {
            finish()
            return
        }
        listState.state(state)
        operationCoordinator.render(state.operation)
    }

    private fun preview(file: ScannedFile) {
        // FileProvider 只发出单个文件的临时只读授权，绝不向外暴露 file:// URI 或目录授权。
        lifecycleScope.launch {
            try {
                val uri =
                    container.taskExecutor.io {
                        val access = FileContentAccess(this@FileCleanupActivity)
                        access.validate(file)
                        if (file.backend == FileBackend.DIRECT)
                            FileProvider.getUriForFile(
                                this@FileCleanupActivity,
                                "$packageName.cleanup.files",
                                access.validatedFile(file),
                            )
                        else Uri.parse(file.uri)
                    }
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, file.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            } catch (_: ActivityNotFoundException) {
                toast(R.string.cleanup_view_unavailable)
            } catch (_: java.io.IOException) {
                toast(R.string.cleanup_action_failed)
            } catch (_: SecurityException) {
                toast(R.string.cleanup_action_failed)
            }
        }
    }

    private fun quality(file: ScannedFile) {
        val values = intArrayOf(60, 75, 85)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cleanup_quality)
            .setSingleChoiceItems(
                arrayOf(
                    getString(R.string.cleanup_quality_small),
                    getString(R.string.cleanup_quality_balanced),
                    getString(R.string.cleanup_quality_high),
                ),
                values.indexOf(file.quality),
            ) { dialog, index ->
                model.quality(file.id, values[index])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cleanup_cancel, null)
            .show()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResumeFragments() {
        super.onResumeFragments()
        render(model.state.value)
        adapter?.resume()
    }

    override fun onStop() {
        filters.close()
        adapter?.pause()
        if (!isChangingConfigurations) model.onBackground()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        operationCoordinator.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        binding.cleanupFiles.adapter = null
        adapter?.pause()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SCAN = "cleanup.scan.id"
        const val EXTRA_BUCKET = "cleanup.bucket"
    }
}
