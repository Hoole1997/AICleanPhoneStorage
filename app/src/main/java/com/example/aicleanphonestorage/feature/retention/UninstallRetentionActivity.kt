package com.example.aicleanphonestorage.feature.retention

import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.databinding.ScreenUninstallRetentionBinding
import com.example.aicleanphonestorage.feature.startup.StartupEntry
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import io.docview.push.NotificationDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 独立宿主和两份 XML 页面；系统长按菜单由 Launcher 绘制，应用不仿造系统卸载/应用详情菜单。 */
class UninstallRetentionActivity : AppCompatActivity() {
    private val model: RetentionViewModel by viewModels {
        viewModelFactory { initializer { RetentionViewModel(createSavedStateHandle()) } }
    }
    private val runtime get() = (application as CleanApplication).container.uninstallRetention
    private lateinit var binding: ScreenUninstallRetentionBinding
    private lateinit var renderer: RetentionRenderer
    private lateinit var ads: InterstitialActions
    private lateinit var uninstall: SelfUninstallCoordinator
    private var allowed = false
    private var pendingHome = false
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        binding = ScreenUninstallRetentionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderer = RetentionRenderer(binding)
        uninstall = SelfUninstallCoordinator(this) { failed ->
            binding.reason.uninstallError.isVisible = failed
            render()
        }
        ads = InterstitialActions(this)
        ads.register(InterstitialPlacements.UNINSTALL_ANYWAY) { model.adFinished() }
        renderer.cards.forEachIndexed { i, card -> card.root.setOnClickListener {
            if (canAct()) {
                val feature = RetentionFeature.entries[i]
                model.feature(feature)
                openHome(feature.destination)
            }
        } }
        binding.offer.offerKeep.setOnClickListener { keep() }
        binding.reason.reasonKeep.setOnClickListener { keep() }
        binding.offer.uninstallAnyway.setOnClickListener {
            if (canAct() && model.beginAnyway()) ads.run(InterstitialPlacements.UNINSTALL_ANYWAY, InterstitialPlacements.UNINSTALL_ANYWAY)
        }
        binding.reason.uninstall.setOnClickListener {
            if (canAct()) { model.uninstall(); uninstall.launch() }
        }
        binding.reason.back.setOnClickListener { back() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        model.reason?.let { binding.reason.reasons.check(renderer.choices[it].id) }
        binding.reason.reasons.setOnCheckedChangeListener { _, id ->
            model.reason = renderer.choices.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
        binding.reason.feedback.setText(model.feedback)
        binding.reason.feedback.doAfterTextChanged { model.feedback = it?.toString().orEmpty() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // 插画延伸到状态栏；可操作内容只补一次左右/底部 inset，原因页额外避开键盘。
            binding.offer.root.setPadding(bars.left, 0, bars.right, bars.bottom)
            binding.reason.root.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        render()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) {
            allowed = false
            render()
            val enabled = try { runtime.checkNow() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            allowed = enabled
            if (!enabled) pendingHome = true
            render()
            continueHome()
            // 成功的远程刷新/归因变化会关闭正在显示的旧入口，后台不保持页面观察。
            runtime.allowed.collect { value ->
                if (value == false) { allowed = false; pendingHome = true; render(); continueHome() }
            }
        } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { model.state.collect { render() } } }
    }

    private fun render() {
        if (!this::uninstall.isInitialized) return
        renderer.render(model.state.value, allowed, uninstall.busy)
        if (allowed && !pendingHome && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.shown()
    }

    private fun canAct() = allowed && !pendingHome && !leaving && !ads.busy && !uninstall.busy && !model.state.value.busy

    private fun keep() { if (canAct()) { model.keep(); openHome() } }
    private fun back() {
        if (ads.busy || uninstall.busy || model.state.value.busy) return
        androidx.core.view.WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        if (!model.back()) finish()
    }

    private fun continueHome() {
        if (pendingHome && hasWindowFocus() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) openHome()
    }

    private fun openHome(destination: NotificationDestination = NotificationDestination.HOME) {
        if (leaving || isFinishing) return
        leaving = true
        // 复用首页各功能入口，继续走原有声明/权限/广告规则；不伪造通知点击或直接启动扫描。
        StartupNavigation.openHome(this, StartupEntry(destination), android.animation.ValueAnimator.areAnimatorsEnabled())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && this::binding.isInitialized) continueHome()
    }

    override fun onPause() {
        // 旧的准入结果不能让广告返回时、复核之前上报新页曝光或接受按钮操作。
        allowed = false
        super.onPause()
    }
}
