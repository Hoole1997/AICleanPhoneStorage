package com.example.aicleanphonestorage.feature.junkcleaner.ui

import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.example.aicleanphonestorage.app.ad.NativeAdCoordinator
import com.example.aicleanphonestorage.app.ad.NativeAdFeature
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.FeatureExitCoordinator
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.databinding.ScreenJunkCleaningBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 总览只显示摘要；分类详情复用共享分页页，最终清理复用同一操作协调器。 */
class JunkCleaningActivity : AppCompatActivity() {
    private val container
        get() = (application as CleanApplication).container

    private val scanId
        get() = intent.getLongExtra(FileCleanupActivity.EXTRA_SCAN, -1)

    private val cleanup: CleanupViewModel by viewModels {
        viewModelFactory {
            initializer {
                CleanupViewModel(
                    container.fileScanRepository,
                    container.fileOperations,
                    createSavedStateHandle(),
                    scanId,
                )
            }
        }
    }
    private val overview: JunkOverviewViewModel by viewModels {
        viewModelFactory {
            initializer { JunkOverviewViewModel(container.junkSummaryRepository, scanId) }
        }
    }
    private var lastError = 0L
    private lateinit var ads: InterstitialActions
    private lateinit var exit: FeatureExitCoordinator
    private lateinit var binding: ScreenJunkCleaningBinding
    private lateinit var operations: CleanupOperationCoordinator
    private lateinit var adapter: JunkCategoriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastError = savedInstanceState?.getLong("junk.error") ?: 0
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenJunkCleaningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.example.aicleanphonestorage.core.analytics.PageTelemetry.attach(this, "junk",
            com.example.aicleanphonestorage.app.analytics.FeatureTelemetry.permission(application as CleanApplication, "junk"))
        NativeAdCoordinator(this, binding.nativeAd, NativeAdFeature.JUNK.featureSlot)
        ads = InterstitialActions(this)
        operations = CleanupOperationCoordinator(this, cleanup, savedInstanceState, ads)
        exit = FeatureExitCoordinator(this, { InterstitialPlacements.exit(CleanupFeature.SMART_CLEAN) }, isBusy = { ads.busy })
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.junkContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        ViewCompat.setAccessibilityHeading(binding.junkTitle, true)
        binding.junkBack.setOnClickListener { exit.exit() }
        binding.junkClean.setOnClickListener {
            if (!ads.busy) cleanup.prepare()
        }
        adapter =
            JunkCategoriesAdapter(::open) { kind, selected ->
                cleanup.selectBucket(kind.name, selected)
            }
        binding.junkCategories.layoutManager = LinearLayoutManager(this)
        binding.junkCategories.adapter = adapter
        (binding.junkCategories.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations =
            false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(overview.state, cleanup.state) { summary, work -> summary to work }
                    .collect { (summary, work) -> render(summary, work) }
            }
        }
    }

    private fun open(kind: JunkKind) {
        com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry().junkGroup(kind)
        startActivity(
            Intent(this, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, scanId)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.SMART_CLEAN.name)
                .putExtra(FileCleanupActivity.EXTRA_BUCKET, kind.name)
        )
    }

    private fun render(summary: JunkOverviewState, work: CleanupUiState) {
        if (work.error > lastError) {
            lastError = work.error
            Toast.makeText(this, R.string.cleanup_action_failed, Toast.LENGTH_LONG).show()
        }
        if (summary.failed) {
            Toast.makeText(this, R.string.cleanup_scan_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val empty=summary.snapshot?.categories?.all{it.count==0}==true
        binding.junkEmpty.isVisible=empty
        binding.junkCategories.isVisible=summary.snapshot!=null && !empty
        binding.junkPageProgress.isVisible=summary.snapshot==null && !summary.failed
        binding.junkFooter.isVisible=summary.snapshot!=null && !empty
        binding.junkClean.isVisible=!empty
        summary.snapshot?.let { snapshot ->
            val enabled = work.operation == CleanupOperationState.Idle && work.editing == 0
            adapter.submit(snapshot, enabled)
            binding.junkClean.isEnabled = enabled && snapshot.categories.any { it.selected > 0 }
            binding.junkScope.text =
                getString(
                    R.string.junk_scope,
                    scanScopeText(snapshot.handle?.scopeLabel.orEmpty()),
                    snapshot.handle?.analysisSkipped ?: 0,
                )
            binding.junkScope.isVisible =
                snapshot.handle?.scopeLabel == "Selected folder" ||
                    (snapshot.handle?.analysisSkipped ?: 0) > 0
        }
        if(empty && binding.junkScope.isVisible)binding.junkFooter.isVisible=true
        operations.render(work.operation)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        cleanup.refreshTotals()
        render(overview.state.value, cleanup.state.value)
    }

    override fun onStop() {
        if (!isChangingConfigurations) cleanup.onBackground()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("junk.error", lastError)
        operations.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        binding.junkCategories.adapter = null
        super.onDestroy()
    }
}
