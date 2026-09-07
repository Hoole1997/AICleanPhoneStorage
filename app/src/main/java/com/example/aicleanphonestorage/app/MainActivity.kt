package com.example.aicleanphonestorage.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.home.ui.HomeRenderer
import com.example.aicleanphonestorage.feature.home.ui.HomeUiActions
import com.example.aicleanphonestorage.feature.home.ui.HomeViewModel
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficActivity
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficViewModel
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficEntryCoordinator
import kotlinx.coroutines.launch

/** 只负责窗口和生命周期。系统状态栏由 Android 绘制，不用设计稿的 iOS 图标/时间冒充。 */
class MainActivity : AppCompatActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer { HomeViewModel((application as CleanApplication).container.homeOverviewRepository) }
        }
    }
    private lateinit var renderer: HomeRenderer
    private var previewSelection: String? = null
    private val trafficEntry: NetworkTrafficViewModel by viewModels {
        viewModelFactory { initializer {
            NetworkTrafficViewModel((application as CleanApplication).container.networkTrafficRepository, createSavedStateHandle(), entryMode = true)
        } }
    }
    private lateinit var trafficEntryCoordinator: NetworkTrafficEntryCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val binding = ScreenHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.pageTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.homeContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        trafficEntryCoordinator = NetworkTrafficEntryCoordinator(this, trafficEntry, (application as CleanApplication).container.trafficSnapshotTransfer)
        renderer = HomeRenderer(binding, object : HomeUiActions by HomeUiActions.None {
            override fun onToolSelected(tool: HomeTool) {
                if (tool == HomeTool.Network) trafficEntry.beginEntry()
            }
        })
        binding.retryButton.setOnClickListener { homeViewModel.retry() }
        previewSelection = HomePreviewSupport.initialSelection(intent, savedInstanceState)
        HomePreviewSupport.attach(binding.pageTitle) { selection ->
            previewSelection = selection
            renderCurrentState()
        }
        renderCurrentState()
        if (intent.getBooleanExtra(NetworkTrafficActivity.EXTRA_REENTER, false)) {
            intent.removeExtra(NetworkTrafficActivity.EXTRA_REENTER)
            trafficEntry.beginEntry()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { trafficEntry.state.collect(trafficEntryCoordinator::render) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { state -> renderer.render(state, HomePreviewSupport.content(previewSelection)) }
            }
        }
    }

    private fun renderCurrentState() = renderer.render(homeViewModel.uiState.value, HomePreviewSupport.content(previewSelection))

    override fun onResume() { super.onResume(); trafficEntry.onForeground() }
    override fun onResumeFragments() { super.onResumeFragments(); trafficEntryCoordinator.render(trafficEntry.state.value) }
    override fun onStop() {
        if (!isChangingConfigurations) trafficEntry.onBackground()
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(NetworkTrafficActivity.EXTRA_REENTER, false)) trafficEntry.beginEntry()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Bundle 只保存小型预览模式标记，滚动位置交给 RecyclerView，绝不存入列表或位图。
        previewSelection?.let { outState.putString(HomePreviewSupport.STATE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        renderer.dispose()
        super.onDestroy()
    }
}
