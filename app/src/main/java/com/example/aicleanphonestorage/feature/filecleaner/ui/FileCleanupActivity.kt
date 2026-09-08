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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
                )
            }
        }
    }
    private lateinit var binding: ScreenFileCleanupBinding
    private lateinit var filters: CleanupFilters
    private var adapter: CleanupFilesAdapter? = null
    private var lastError = 0L
    private var originalsConfirmation: Long? = null
    private val consent =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            model.consentResult(it.resultCode == RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalsConfirmation = savedInstanceState?.getLong("originals")?.takeIf { it > 0 }
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenFileCleanupBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
        binding.cleanupEmpty.setOnClickListener { adapter?.retry() }
        filters = CleanupFilters(binding, model::setFilter)
        supportFragmentManager.setFragmentResultListener(WORK_CANCEL, this) { _, _ ->
            model.onBackground()
        }
        supportFragmentManager.setFragmentResultListener(CleanupMessageDialog.RESULT, this) {
            _,
            result ->
            val identity = result.getString("identity").orEmpty()
            val positive = result.getString("action") == "positive"
            val op = model.state.value.operation
            when {
                identity.startsWith("originals:") -> {
                    val id = originalsConfirmation
                    originalsConfirmation = null
                    if (positive && id != null) model.removeOriginals(id)
                    else model.dismissOperation()
                }
                op is CleanupOperationState.Confirm ->
                    if (positive) model.confirm(op.id) else model.dismissOperation()
                op is CleanupOperationState.Result ->
                    if (positive && op.summary.originalsAvailable > 0) {
                        originalsConfirmation = op.id
                        render(model.state.value)
                    } else model.dismissOperation()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    private fun attach(feature: CleanupFeature) {
        if (adapter != null) return
        val columns =
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
            )
        adapter = files
        binding.cleanupFiles.adapter = files
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.files.collectLatest(files::submitData) }
                launch {
                    files.loadStateFlow.collect {
                        binding.cleanupEmpty.isVisible =
                            files.itemCount == 0 && it.refresh !is LoadState.Loading
                        binding.cleanupEmpty.setText(
                            if (it.refresh is LoadState.Error) R.string.cleanup_scan_failed
                            else R.string.cleanup_no_files
                        )
                    }
                }
            }
        }
    }

    private fun render(state: CleanupUiState) {
        val handle = state.handle
        if (handle != null) {
            attach(handle.feature)
            binding.cleanupTitle.setText(handle.feature.titleRes)
            binding.cleanupPhotoHeader.isVisible = handle.feature == CleanupFeature.PHOTO_COMPRESS
            binding.cleanupFilters.isVisible = handle.feature == CleanupFeature.LARGE_FILES
            binding.cleanupUnusedAge.isVisible = handle.feature == CleanupFeature.UNUSED_FILES
            binding.cleanupScope.isVisible =
                handle.partial || handle.scopeLabel == "Selected folder"
            binding.cleanupScope.text =
                if (handle.partial) getString(R.string.cleanup_limited) else handle.scopeLabel
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
        renderOperation(state.operation)
    }

    private fun renderOperation(operation: CleanupOperationState) {
        if (
            supportFragmentManager.isStateSaved ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val loading =
            supportFragmentManager.findFragmentByTag(WORKING) as? TaskLoadingDialogFragment
        if (operation is CleanupOperationState.Running) {
            val frame =
                LoadingUiState(
                    operation.id,
                    getString(R.string.cleanup_working, operation.done, operation.total),
                    getString(R.string.cleanup_processing_files),
                    if (operation.total > 0) operation.done * 100 / operation.total else null,
                    cancellable = true,
                    showAd = false,
                    resultKey = WORK_CANCEL,
                )
            if (loading == null)
                TaskLoadingDialogFragment.newInstance(frame)
                    .showNow(supportFragmentManager, WORKING)
            else loading.render(frame)
        } else loading?.dismiss()
        val old =
            supportFragmentManager.findFragmentByTag(CleanupMessageDialog.TAG)
                as? CleanupMessageDialog
        val message =
            when {
                originalsConfirmation != null ->
                    CleanupMessageDialog.create(
                        "originals:$originalsConfirmation",
                        getString(R.string.cleanup_tips),
                        getString(R.string.cleanup_originals_confirm),
                        getString(R.string.cleanup_confirm),
                        getString(R.string.cleanup_cancel),
                    )
                operation is CleanupOperationState.Confirm ->
                    CleanupMessageDialog.create(
                        "confirm:${operation.id}",
                        getString(R.string.cleanup_tips),
                        if (operation.compress)
                            getString(R.string.cleanup_compress_confirm, operation.count)
                        else
                            getString(
                                R.string.cleanup_delete_confirm,
                                operation.count,
                                Formatter.formatShortFileSize(this, operation.bytes),
                            ),
                        getString(R.string.cleanup_confirm),
                        getString(R.string.cleanup_cancel),
                    )
                operation is CleanupOperationState.Result ->
                    CleanupMessageDialog.create(
                        "result:${operation.id}",
                        getString(R.string.cleanup_done),
                        getString(
                            R.string.cleanup_result,
                            operation.summary.deleted,
                            operation.summary.copied,
                            operation.summary.skipped,
                            operation.summary.failed,
                        ),
                        getString(
                            if (operation.summary.originalsAvailable > 0)
                                R.string.cleanup_remove_originals
                            else R.string.cleanup_done
                        ),
                        getString(
                            if (operation.summary.originalsAvailable > 0)
                                R.string.cleanup_keep_originals
                            else R.string.cleanup_cancel
                        ),
                    )
                else -> null
            }
        if (old?.identity != message?.identity) {
            old?.dismissNow()
            message?.showNow(supportFragmentManager, CleanupMessageDialog.TAG)
        }
        if (operation is CleanupOperationState.Consent && !model.waitingSystem) {
            model.consentLaunched()
            try {
                consent.launch(IntentSenderRequest.Builder(operation.sender).build())
            } catch (_: android.content.IntentSender.SendIntentException) {
                model.consentResult(false)
                toast(R.string.cleanup_action_failed)
            }
        }
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
        originalsConfirmation?.let { outState.putLong("originals", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        binding.cleanupFiles.adapter = null
        adapter?.pause()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SCAN = "cleanup.scan.id"
        private const val WORKING = "cleanup.operation.loading"
        private const val WORK_CANCEL = "cleanup.operation.cancel"
    }
}
