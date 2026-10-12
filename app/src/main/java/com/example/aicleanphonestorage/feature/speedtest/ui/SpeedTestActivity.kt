package com.example.aicleanphonestorage.feature.speedtest.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.FeatureExitCoordinator
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.analytics.PageTelemetry
import com.example.aicleanphonestorage.databinding.ScreenSpeedTestBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面只负责窗口、生命周期与弹框宿主；测速与统计在仓库层，状态由 ViewModel 单向提供。 */
class SpeedTestActivity : AppCompatActivity() {
    private lateinit var ads: InterstitialActions
    private val model: SpeedTestViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as CleanApplication).container
                SpeedTestViewModel(container.speedTestRepository) {
                    withContext(Dispatchers.IO) { container.speedTestNetwork.current() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val binding = ScreenSpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.speedTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.speedContent.setPadding(bars.left, bars.top, bars.right, 0)
            val density = resources.displayMetrics.density
            // 与电池结果页一致：大屏保留稿件底部 100dp，小屏/大字号优先内容区；底部至少让出系统导航栏。
            val compact = resources.configuration.screenHeightDp < 800 || resources.configuration.fontScale >= 1.5f
            binding.speedFooter.setPadding(0, (36 * density).toInt(), 0,
                maxOf(bars.bottom, ((if (compact) 24 else 100) * density).toInt()))
            // 测速中没有底部按钮，滚动内容自行让出导航栏。
            binding.speedScroll.setPadding(0, 0, 0, if (binding.speedFooter.isVisible) 0 else bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        ads = InterstitialActions(this)
        // 测速成功后先请求 scan_complete_speedtest，回调（关闭/无填充/失败）后由当前 Activity 揭示结果。
        ads.register(InterstitialPlacements.SPEED_TEST_SCAN, model::revealResult)
        // 顶部返回、系统返回与失败弹框关闭页面都回首页，由首页恢复并绘制后请求 back_home_speedtest；
        // 扫描完成插屏进行中不重复离开，避免两个插屏叠加。
        val exit = FeatureExitCoordinator(this, { InterstitialPlacements.SPEED_TEST_EXIT }, isBusy = { ads.busy })
        binding.speedBack.setOnClickListener { exit.exit() }
        binding.speedAgain.setOnClickListener { model.start() }
        supportFragmentManager.setFragmentResultListener(SpeedTestFailureDialog.RESULT, this) { _, result ->
            if (result.getString(SpeedTestFailureDialog.ACTION) == SpeedTestFailureDialog.RETRY) model.start()
            else if (model.dismissFailure()) exit.exit()
        }
        val renderer = SpeedTestRenderer(binding)
        val refreshPage = PageTelemetry.attachDynamic(this, {
            if ((model.state.value as? SpeedTestUiState.Done)?.revealed == true) "speedtest_result" else "speedtest"
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    val footerVisible = binding.speedFooter.isVisible
                    renderer.render(state)
                    if (footerVisible != binding.speedFooter.isVisible) ViewCompat.requestApplyInsets(binding.root)
                    showFailure(state)
                    requestScanAd()
                    refreshPage()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestScanAd()
    }

    /**
     * 测速成功且尚未揭示时请求扫描完成插屏，只在页面前台执行；每次成功只请求一次，
     * 等待期间的重复渲染/恢复由 InterstitialActions 的待处理状态拦截。
     */
    private fun requestScanAd() {
        val state = model.state.value
        if (state !is SpeedTestUiState.Done || state.revealed || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        ads.run(InterstitialPlacements.SPEED_TEST_SCAN, InterstitialPlacements.SPEED_TEST_SCAN, model.generation)
    }

    override fun onStart() {
        super.onStart()
        model.onForeground()
    }

    override fun onStop() {
        // 离开前台取消测速，避免后台持续消耗流量；配置变更由 ViewModel 保留进行中的测速。
        if (!isChangingConfigurations) model.onBackground()
        super.onStop()
    }

    private fun showFailure(state: SpeedTestUiState) {
        val manager = supportFragmentManager
        if (manager.isStateSaved) return
        val existing = manager.findFragmentByTag(SpeedTestFailureDialog.TAG) as? SpeedTestFailureDialog
        if (state is SpeedTestUiState.Failed) {
            if (existing == null) SpeedTestFailureDialog.create(state.reason).showNow(manager, SpeedTestFailureDialog.TAG)
        } else existing?.dismiss()
    }
}
