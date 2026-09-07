package com.example.aicleanphonestorage.feature.networktraffic.ui

import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.databinding.ScreenNetworkTrafficBinding
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficApp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NetworkTrafficActivity : AppCompatActivity() {
    private val viewModel: NetworkTrafficViewModel by viewModels {
        viewModelFactory { initializer {
            NetworkTrafficViewModel((application as CleanApplication).container.networkTrafficRepository, createSavedStateHandle(),
                initialSnapshot = (application as CleanApplication).container.trafficSnapshotTransfer.take(intent.getLongExtra(EXTRA_SNAPSHOT_TOKEN, 0)))
        } }
    }
    private lateinit var renderer: NetworkTrafficRenderer
    private var returningToEntry = false
    private var managementJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        val binding = ScreenNetworkTrafficBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.trafficTitle, true)
        binding.trafficBack.setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.trafficContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        val container = (application as CleanApplication).container
        renderer = NetworkTrafficRenderer(binding, lifecycleScope, AppIconLoader(this, container.taskExecutor),
            viewModel::selectPeriod, ::manageApp) {
            if (viewModel.state.value.status == TrafficStatus.NeedsAccess) returnToEntry()
            else viewModel.selectPeriod(viewModel.state.value.period)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect { render(it) } }
        }
    }

    override fun onStart() { super.onStart(); renderer.setActive(true) }
    override fun onResume() { super.onResume(); viewModel.onForeground() }
    override fun onResumeFragments() { super.onResumeFragments(); render(viewModel.state.value) }
    override fun onStop() {
        managementJob?.cancel()
        renderer.setActive(false)
        // 配置变更保留查询；真正离开前台才取消。进入系统设置前没有必要保留旧读取任务。
        if (!isChangingConfigurations) viewModel.onBackground()
        super.onStop()
    }
    override fun onDestroy() { renderer.dispose(); super.onDestroy() }

    private fun render(state: TrafficUiState) {
        renderer.render(state)
        // 页面内部绝不展示任务弹窗；权限撤销/进程恢复缺权限时回首页入口重新授权。
        if (state.status == TrafficStatus.NeedsAccess && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) returnToEntry()
    }

    private fun returnToEntry() {
        if (returningToEntry) return
        returningToEntry = true
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_REENTER, true))
        finish()
    }

    private fun manageApp(app: TrafficApp) {
        if (app.packages.size == 1) openAppSettings(app.packages.single().packageName)
        else if (app.packages.size > 1) MaterialAlertDialogBuilder(this)
            .setTitle(R.string.traffic_shared_apps)
            .setItems(app.packages.map { it.label }.toTypedArray()) { _, index -> openAppSettings(app.packages[index].packageName) }
            .show()
    }
    private fun openAppSettings(packageName: String) {
        if (managementJob?.isActive == true) return
        managementJob = lifecycleScope.launch {
            try {
                (application as CleanApplication).container.taskExecutor.io {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            } catch (error: android.content.pm.PackageManager.NameNotFoundException) { message(R.string.traffic_app_missing) }
            catch (error: ActivityNotFoundException) { message(R.string.traffic_settings_missing) }
            catch (error: SecurityException) { message(R.string.traffic_settings_missing) }
        }
    }

    companion object {
        const val EXTRA_SNAPSHOT_TOKEN = "network_traffic.snapshot_token"
        const val EXTRA_REENTER = "network_traffic.reenter"
    }

    private fun message(resource: Int) = Toast.makeText(this, resource, Toast.LENGTH_SHORT).show()
}
