package com.example.aicleanphonestorage.feature.onboarding

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
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.databinding.ScreenDeviceScanBinding
import com.example.aicleanphonestorage.feature.startup.StartupEntry
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import io.docview.push.NotificationDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 独立竖屏 Activity；设备读取、渲染、持久化和广告分别负责，不扩充首页职责。 */
class DeviceScanActivity : AppCompatActivity() {
    private val model: DeviceScanViewModel by viewModels {
        viewModelFactory { initializer {
            val container = (application as CleanApplication).container
            DeviceScanViewModel(createSavedStateHandle(), container.deviceInfoSource, container.newUserFlow)
        } }
    }
    private lateinit var renderer: DeviceScanRenderer
    private lateinit var ads: InterstitialActions
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程死亡后从系统任务栈恢复时也回启动页，由持久化阶段决定是否再次扫描。
        // 若已进入插屏，直接恢复首页，不重放那次点击或广告。
        if (model.processRestored) {
            navigating = true
            lifecycleScope.launch {
                val pending = try { (application as CleanApplication).container.newUserFlow.eligible() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                if (pending) {
                    startActivity(StartupNavigation.startupIntent(this@DeviceScanActivity,
                        StartupEntry(NotificationDestination.HOME, launcher = true)))
                    finish()
                } else StartupNavigation.openHome(this@DeviceScanActivity, StartupEntry(NotificationDestination.HOME), false)
            }
            return
        }
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        val binding = ScreenDeviceScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.scanScroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            val gutter = resources.getDimensionPixelSize(R.dimen.new_user_page_gutter)
            val compact = resources.configuration.screenHeightDp < 850 || resources.configuration.fontScale >= 1.5f
            binding.scanContent.setPadding(gutter, resources.getDimensionPixelSize(R.dimen.new_user_header_space), gutter,
                resources.getDimensionPixelSize(if (compact) R.dimen.new_user_compact_bottom_space else R.dimen.new_user_bottom_space))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        renderer = DeviceScanRenderer(binding)
        ads = InterstitialActions(this)
        ads.register(START_CLEAN) { model.adFinished() }
        binding.scanAction.setOnClickListener {
            if (model.state.value.engineFailed) model.retry() else model.startClean()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect {
                    renderer.render(it)
                    continueIfReady()
                }
            }
        }
    }

    private fun continueIfReady() {
        if (navigating || isFinishing || isDestroyed || !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !this::ads.isInitialized) return
        if (model.state.value.navigateHome) {
            navigating = true
            StartupNavigation.openHome(this, StartupEntry(NotificationDestination.HOME), android.animation.ValueAnimator.areAnimatorsEnabled())
        } else if (!ads.busy && model.takeAdRequest()) {
            ads.run(START_CLEAN, START_CLEAN)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::renderer.isInitialized) {
            renderer.resumed(true)
            model.foreground()
            continueIfReady()
        }
    }

    override fun onPause() {
        if (this::renderer.isInitialized) renderer.resumed(false)
        model.background()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (this::renderer.isInitialized) {
            renderer.windowFocus(hasFocus)
            if (hasFocus) continueIfReady()
        }
    }

    override fun onDestroy() {
        if (this::renderer.isInitialized) renderer.dispose()
        super.onDestroy()
    }

    internal companion object { const val START_CLEAN = "new_user_start_clean" }
}
