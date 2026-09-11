package com.example.aicleanphonestorage.app

import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import com.example.aicleanphonestorage.app.ad.HomeExitAdCoordinator

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.core.permissions.*
import com.example.aicleanphonestorage.core.ui.loading.observeEntryState
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.appmanager.ui.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.feature.home.ui.HomeUiActions
import com.example.aicleanphonestorage.feature.home.ui.HomeRenderer
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.home.ui.HomeEntryActions
import com.example.aicleanphonestorage.feature.home.ui.HomeViewModel
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficActivity
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficEntryCoordinator
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficViewModel
import com.example.aicleanphonestorage.feature.notifications.ui.NotificationCleanerActivity
import com.example.aicleanphonestorage.feature.notifications.ui.NotificationCleanerViewModel
import com.example.aicleanphonestorage.feature.notifications.ui.NotificationEntryCoordinator
import kotlinx.coroutines.launch
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import com.example.aicleanphonestorage.feature.push.PushPermissionCoordinator
import com.example.aicleanphonestorage.feature.push.PushPermissionViewModel
import io.docview.push.NotificationDestination
import com.example.aicleanphonestorage.feature.startup.StartupNavigation

/** 只负责窗口和生命周期。系统状态栏由 Android 绘制，不用设计稿的 iOS 图标/时间冒充。 */
class MainActivity : AppCompatActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel((application as CleanApplication).container.homeOverviewRepository,
                    (application as CleanApplication).homeCleaning.state)
            }
        }
    }
    private val permissionFlow: PermissionFlowViewModel by viewModels {
        viewModelFactory {
            initializer {
                PermissionFlowViewModel(
                    (application as CleanApplication).container.permissionAccess,
                    createSavedStateHandle(),
                )
            }
        }
    }
    private val pushPermissionModel: PushPermissionViewModel by viewModels {
        viewModelFactory { initializer { PushPermissionViewModel(createSavedStateHandle()) } }
    }
    private var redirectedToStartup = false
    private lateinit var homeExitAds: HomeExitAdCoordinator
    private lateinit var homeActions: HomeEntryActions
    private lateinit var pushPermission: PushPermissionCoordinator
    private lateinit var permissions: PermissionCoordinator
    private lateinit var renderer: HomeRenderer
    private var previewSelection: String? = null
    private val trafficEntry: NetworkTrafficViewModel by viewModels {
        viewModelFactory {
            initializer {
                NetworkTrafficViewModel(
                    (application as CleanApplication).container.networkTrafficRepository,
                    createSavedStateHandle(),
                    entryMode = true,
                )
            }
        }
    }
    private val notificationEntry: NotificationCleanerViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as CleanApplication).container
                NotificationCleanerViewModel(
                    container.notificationAppsRepository,
                    container.notificationConnection.connected,
                    createSavedStateHandle(),
                    entry = true,
                )
            }
        }
    }
    private val cleanupEntry: CleanupEntryViewModel by viewModels {
        viewModelFactory {
            initializer {
                CleanupEntryViewModel(
                    (application as CleanApplication).container.fileScanRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
    private val appManagerEntry: AppManagerEntryViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppManagerEntryViewModel(
                    (application as CleanApplication).container.appManagerRepository
                )
            }
        }
    }
    private lateinit var appManagerCoordinator: AppManagerEntryCoordinator
    private lateinit var cleanupCoordinator: CleanupEntryCoordinator
    private lateinit var notificationCoordinator: NotificationEntryCoordinator
    private lateinit var trafficEntryCoordinator: NetworkTrafficEntryCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 兼容升级前已经存在的、仍指向 MainActivity 的通知 PendingIntent。
        if (StartupNavigation.needsStartup(intent)) {
            redirectedToStartup = true
            startActivity(StartupNavigation.startupIntent(this, StartupNavigation.read(intent)))
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val binding = ScreenHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.pageTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.homeContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        permissions =
            PermissionCoordinator(
                this,
                permissionFlow,
                (application as CleanApplication).container.permissionAccess,
            )
        trafficEntryCoordinator =
            NetworkTrafficEntryCoordinator(
                this,
                trafficEntry,
                (application as CleanApplication).container.trafficSnapshotTransfer,
                permissions,
            )
        notificationCoordinator =
            NotificationEntryCoordinator(
                this,
                notificationEntry,
                (application as CleanApplication).container.notificationCatalogTransfer,
                permissions,
            )
        cleanupCoordinator = CleanupEntryCoordinator(this, cleanupEntry, permissions)
        observeEntryState(cleanupEntry.state, cleanupCoordinator::render)
        appManagerCoordinator =
            AppManagerEntryCoordinator(
                this,
                appManagerEntry,
                (application as CleanApplication).container.appManagerTransfer,
            )
        observeEntryState(appManagerEntry.state, appManagerCoordinator::render)
        homeActions = HomeEntryActions(
            permissions, trafficEntry, notificationEntry, cleanupEntry, appManagerEntry,
            openSettings = {
                startActivity(Intent(this, com.example.aicleanphonestorage.feature.settings.SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            },
        )
        val nativeHome = layoutInflater.inflate(R.layout.view_native_ad_slot, binding.homeContent, false) as android.view.ViewGroup
        val app = application as CleanApplication
        val trackedActions = object : HomeUiActions by homeActions {
            override fun onSmartClean() {
                if (previewSelection == null) {
                    BusinessTelemetry.emit(MetricEvent.CLEAN_NOW_CLICK, mapOf("state" to if (app.homeCleaning.state.value.dirty) "dirty" else "cleaned"))
                    FeatureTelemetry.entry(app, "junk")
                }
                homeActions.onSmartClean()
            }
            override fun onToolSelected(tool: HomeTool) {
                val entry = when (tool) {
                    HomeTool.Network -> "traffic"; HomeTool.Notifications -> "notify"; HomeTool.Apps -> "apps"
                    HomeTool.Compress -> "photo"; HomeTool.LargeFiles -> "large"; HomeTool.UnusedFiles -> "unused"; HomeTool.Screenshots -> "screenshots"
                }
                if (previewSelection == null) FeatureTelemetry.entry(app, entry)
                homeActions.onToolSelected(tool)
            }
        }
        renderer = HomeRenderer(binding, trackedActions, nativeHome)
        PageTelemetry.attach(this, "home")
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) = trackHomeState()
        })
        com.example.aicleanphonestorage.app.ad.NativeAdCoordinator(this, nativeHome,
            com.example.aicleanphonestorage.app.ad.NativeAdPlacements.HOME)
        homeExitAds = HomeExitAdCoordinator(this, binding.root)
        if (homeExitAds.accept(intent)) homeActions.cancelPending()
        if (intent.getBooleanExtra(StartupNavigation.PERMISSION_COMPLETED, false))
            pushPermissionModel.completeFromPreviousHost()
        pushPermission = PushPermissionCoordinator.attach(this,
            (application as CleanApplication).notificationRuntime, pushPermissionModel, permissions,
            beforeSettings = homeActions::cancelPending)
        binding.retryButton.setOnClickListener { homeViewModel.retry() }
        previewSelection = HomePreviewSupport.initialSelection(intent, savedInstanceState)
        HomePreviewSupport.attach(binding.pageTitle) { selection ->
            previewSelection = selection
            renderCurrentState()
        }
        renderCurrentState()
        handleNotificationIntent(intent)
        if (intent.getBooleanExtra(NetworkTrafficActivity.EXTRA_REENTER, false)) {
            intent.removeExtra(NetworkTrafficActivity.EXTRA_REENTER)
            trafficEntry.beginEntry()
        }
        if (intent.getBooleanExtra(NotificationCleanerActivity.EXTRA_REENTER, false)) {
            intent.removeExtra(NotificationCleanerActivity.EXTRA_REENTER)
            notificationEntry.beginEntry()
        }
        observeEntryState(notificationEntry.state, notificationCoordinator::render)
        observeEntryState(trafficEntry.state, trafficEntryCoordinator::render)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { state ->
                    renderer.render(state, HomePreviewSupport.content(previewSelection))
                    trackHomeState()
                }
            }
        }
    }

    private val homeExposure by viewModels<HomeExposureState>()

    private fun trackHomeState() {
        if (previewSelection != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            homeViewModel.uiState.value !is com.example.aicleanphonestorage.feature.home.ui.HomeUiState.Ready) return
        val params = FeatureTelemetry.homeState((application as CleanApplication).homeCleaning.state.value)
        if (params != homeExposure.last) {
            homeExposure.last = params
            BusinessTelemetry.emit(MetricEvent.HOME_STATE_SHOW, params)
        }
    }

    private fun renderCurrentState() =
        renderer.render(homeViewModel.uiState.value, HomePreviewSupport.content(previewSelection))

    override fun onResume() {
        super.onResume()
        if (redirectedToStartup) return
        (application as CleanApplication).homeCleaning.check()
        renderer.setResumed(true)
        trackHomeState()
        pushPermission.onResume()
        if (!permissions.pending) {
            trafficEntry.onForeground()
            notificationEntry.onForeground()
            cleanupEntry.onForeground()
        }
    }

    override fun onPause() {
        if (this::renderer.isInitialized) renderer.setResumed(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (this::renderer.isInitialized) renderer.setWindowFocused(hasFocus)
        if (this::homeExitAds.isInitialized) homeExitAds.onWindowFocusChanged(hasFocus)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (redirectedToStartup) return
        trafficEntryCoordinator.render(trafficEntry.state.value)
        notificationCoordinator.render(notificationEntry.state.value)
        cleanupCoordinator.render(cleanupEntry.state.value)
        appManagerCoordinator.render(appManagerEntry.state.value)
        pushPermission.drain()
    }

    override fun onStop() {
        if (!isChangingConfigurations) homeExposure.last = null
        if (!redirectedToStartup && !isChangingConfigurations) {
            trafficEntry.onBackground()
            notificationEntry.onBackground()
            cleanupEntry.onBackground()
            appManagerEntry.cancel()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (redirectedToStartup) return
        if (permissions.onReturnIntent(intent)) return
        if (StartupNavigation.needsStartup(intent)) {
            homeActions.cancelPending()
            startActivity(StartupNavigation.startupIntent(this, StartupNavigation.read(intent)))
            return
        }
        setIntent(intent)
        if (intent.getBooleanExtra(StartupNavigation.PERMISSION_COMPLETED, false))
            pushPermissionModel.completeFromPreviousHost()
        if (homeExitAds.accept(intent)) {
            homeActions.cancelPending()
            return
        }
        handleNotificationIntent(intent)
        if (intent.getBooleanExtra(NetworkTrafficActivity.EXTRA_REENTER, false)) {
            permissions.cancel()
            notificationEntry.cancelEntry()
            trafficEntry.beginEntry()
        }
        if (intent.getBooleanExtra(NotificationCleanerActivity.EXTRA_REENTER, false)) {
            permissions.cancel()
            trafficEntry.cancelEntry()
            notificationEntry.beginEntry()
        }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val destination = NotificationNavigation.consume(intent) ?: return
        when (destination) {
            NotificationDestination.HOME -> homeActions.cancelPending()
            NotificationDestination.CLEAN -> homeActions.onSmartClean()
            NotificationDestination.NETWORK -> homeActions.onToolSelected(HomeTool.Network)
            NotificationDestination.PHOTOS -> homeActions.onToolSelected(HomeTool.Compress)
            NotificationDestination.UNUSED_FILES -> homeActions.onToolSelected(HomeTool.UnusedFiles)
            NotificationDestination.SCREENSHOTS -> homeActions.onToolSelected(HomeTool.Screenshots)
            NotificationDestination.LARGE_FILES -> homeActions.onToolSelected(HomeTool.LargeFiles)
            NotificationDestination.NOTIFICATION_CLEANER -> homeActions.onToolSelected(HomeTool.Notifications)
            NotificationDestination.APP_MANAGER -> homeActions.onToolSelected(HomeTool.Apps)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Bundle 只保存小型预览模式标记，滚动位置交给 RecyclerView，绝不存入列表或位图。
        HomePreviewSupport.saveSelection(outState, previewSelection)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (this::renderer.isInitialized) renderer.dispose()
        super.onDestroy()
    }
}
