package com.example.aicleanphonestorage.feature.appmanager.ui

import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.example.aicleanphonestorage.app.ad.NativeAdCoordinator
import com.example.aicleanphonestorage.app.ad.NativeAdFeature
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.FeatureExitCoordinator
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.permissions.*
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ScreenAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.ManagedApp
import kotlinx.coroutines.launch

/** Activity 只处理系统窗口、详情跳转和列表绑定，PackageManager 查询始终由 Repository 执行。 */
class AppManagerActivity : AppCompatActivity() {
    private val container
        get() = (application as CleanApplication).container

    private val model: AppManagerViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppManagerViewModel(
                    container.appManagerRepository,
                    container.appManagerTransfer.take(intent.getLongExtra(EXTRA_CATALOG, 0)),
                    container.taskExecutor,
                    createSavedStateHandle(),
                    resources.configuration.locales[0],
                )
            }
        }
    }
    private val permissionFlow: PermissionFlowViewModel by viewModels {
        viewModelFactory {
            initializer {
                PermissionFlowViewModel(container.permissionAccess, createSavedStateHandle())
            }
        }
    }
    private lateinit var permissions: PermissionCoordinator
    private lateinit var sortControls: AppManagerSortControls
    private lateinit var binding: ScreenAppManagerBinding
    private lateinit var adapter: AppManagerAdapter
    private var latestState = AppManagerUiState()
    private var committed: List<ManagedApp>? = null
    private var submitted: List<ManagedApp>? = null
    private var scrollOnSort = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.example.aicleanphonestorage.core.analytics.PageTelemetry.attach(this, "apps",
            com.example.aicleanphonestorage.app.analytics.FeatureTelemetry.permission(application as CleanApplication, "apps"))
        NativeAdCoordinator(this, binding.nativeAd, NativeAdFeature.APPS.featureSlot)
        ViewCompat.setAccessibilityHeading(binding.appManagerTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.appManagerContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        val exit = FeatureExitCoordinator(this, { InterstitialPlacements.APPS_EXIT })
        binding.appManagerBack.setOnClickListener { exit.exit() }
        binding.appManagerRetry.setOnClickListener { model.refresh() }
        permissions = PermissionCoordinator(this, permissionFlow, container.permissionAccess)
        permissions.register(
            USAGE_ROUTE,
            before = { model.onBackground() },
            result = { model.refresh() },
        )
        binding.appManagerUsageAccess.setOnClickListener {
            permissions.rationale(USAGE_ROUTE, PermissionKind.USAGE)
        }
        sortControls =
            AppManagerSortControls(binding) { key ->
                scrollOnSort = true
                model.selectSort(key)
            }
        val actions = AppManagerActions(this, container.taskExecutor, model::refresh)
        adapter =
            AppManagerAdapter(
                lifecycleScope,
                AppIconLoader(this, container.taskExecutor, 36),
                actions::details,
                { app -> actions.remove(app.packageName, app.sizeBytes) },
            )
        binding.appManagerList.layoutManager = LinearLayoutManager(this)
        binding.appManagerList.adapter = adapter
        (binding.appManagerList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations =
            false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    private fun render(state: AppManagerUiState) {
        latestState = state
        if (state.catalog != null && !state.refreshing && !state.sorting &&
            androidx.lifecycle.ViewModelProvider(this)[com.example.aicleanphonestorage.core.analytics.PageVisitState::class.java].once("apps_page_show")) {
            com.example.aicleanphonestorage.core.analytics.BusinessTelemetry.emit(com.example.aicleanphonestorage.core.analytics.MetricEvent.APPS_PAGE_SHOW,
                mapOf("app_count" to state.catalog.apps.size))
        }
        sortControls.render(state.sort)
        binding.appManagerUsageAccess.isVisible = state.catalog?.usageAccess == false
        if (state.rows !== submitted) {
            submitted = state.rows
            adapter.submitList(state.rows) {
                committed = state.rows
                if (scrollOnSort && !state.sorting) {
                    binding.appManagerList.scrollToPosition(0)
                    scrollOnSort = false
                }
                renderListState()
            }
        }
        renderListState()
    }

    private fun renderListState() {
        val state = latestState
        val busy = state.refreshing || state.sorting
        val empty =
            state.catalog != null &&
                committed === state.rows &&
                adapter.itemCount == 0 &&
                !busy &&
                !state.failed
        binding.appManagerEmpty.isVisible = empty
        binding.appManagerList.isVisible = !empty
        binding.appManagerProgress.isVisible = busy && adapter.itemCount == 0
        binding.appManagerRetry.isVisible = state.failed
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        permissions.onReturnIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        adapter.setActive(true)
    }

    override fun onResume() {
        super.onResume()
        model.onForeground()
    }

    override fun onStop() {
        adapter.setActive(false)
        if (!isChangingConfigurations) model.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        binding.appManagerList.adapter = null
        adapter.setActive(false)
        super.onDestroy()
    }

    companion object {
        private const val USAGE_ROUTE = "permission.appmanager"
        const val EXTRA_CATALOG = "app_manager.catalog"
    }
}
