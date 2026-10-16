package com.example.aicleanphonestorage.feature.startup

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.example.aicleanphonestorage.app.ad.HotStartAdLog
import com.android.common.bill.ads.util.GoogleMobileAdsConsentManager
import com.example.aicleanphonestorage.core.permissions.PermissionCoordinator
import com.example.aicleanphonestorage.core.permissions.PermissionFlowViewModel
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding
import com.example.aicleanphonestorage.feature.push.PushPermissionCoordinator
import com.example.aicleanphonestorage.feature.push.NotificationLaunchTelemetry
import com.example.aicleanphonestorage.feature.push.PushPermissionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 冷启动/通知进入首页；热启动在广告结束后仅关闭自身，恢复原 Activity。 */
class StartupActivity : AppCompatActivity() {
    private val model: StartupViewModel by viewModels {
        viewModelFactory {
            initializer {
                val languages = (application as CleanApplication).languages
                StartupViewModel(createSavedStateHandle(), prepare = { languages.ready.await() })
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
    private lateinit var permissions: PermissionCoordinator
    private lateinit var pushPermission: PushPermissionCoordinator
    private lateinit var renderer: StartupRenderer
    private lateinit var ads: StartupAdCoordinator
    private var consentStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        com.example.aicleanphonestorage.feature.push.ResidentClickTelemetry.consume(intent)
        val incoming = StartupNavigation.read(intent)
        // 在创建 SavedStateHandle 前规范化 Intent，仅允许有界自家通知埋点字段进入状态。
        setIntent(StartupNavigation.startupIntent(this, incoming))
        val binding = ScreenStartupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderer = StartupRenderer(binding)
        lifecycleScope.launch {
            val appResources = applicationContext.resources
            val image =
                withContext(Dispatchers.IO) {
                    appResources.getDrawable(R.drawable.startup_background, null)
                }
            binding.startupBackground.setImageDrawable(image)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            renderer.insets(
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        if (!model.hasEntry()) model.accept(incoming)
        reportNotificationEntry()
        if (model.entry().hotStart) {
            HotStartAdLog.event("startup_created mode=hot restored=${savedInstanceState != null}")
            model.permissionFinished()
        }
        val app = application as CleanApplication
        permissions = PermissionCoordinator(this, permissionFlow, app.container.permissionAccess)
        pushPermission =
            PushPermissionCoordinator.attach(
                this,
                app.notificationRuntime,
                pushPermissionModel,
                permissions,
                position = com.example.aicleanphonestorage.feature.push.PushPermissionPosition.SPLASH,
                allowGuide = false,
            )
        ads = StartupAdCoordinator(this, binding.root, model)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                pushPermissionModel.state.collect { state ->
                    if (state.completed) model.permissionFinished()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    if (!model.entry().hotStart && state.prepared && !pushPermissionModel.state.value.completed)
                        pushPermission.onResume()
                    gatherConsentIfReady()
                    renderer.render(state)
                    if (state.consumed && !isFinishing) finish()
                    proceedIfReady()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 权限返回只恢复当前流程，不能把原通知目标重置为 HOME 或开启另一轮广告。
        if (permissions.onReturnIntent(intent)) return
        com.example.aicleanphonestorage.feature.push.ResidentClickTelemetry.consume(intent)
        val incoming = StartupNavigation.read(intent)
        setIntent(StartupNavigation.startupIntent(this, incoming))
        model.accept(incoming)
        reportNotificationEntry()
        if (incoming.hotStart) model.permissionFinished()
        proceedIfReady()
    }

    private fun reportNotificationEntry() {
        NotificationLaunchTelemetry.entered(model.consumeNotificationEntry())
        // Intent 与 SavedStateHandle 同步清除已消费的埋点快照，只把业务路由交给首页。
        setIntent(StartupNavigation.startupIntent(this, model.entry()))
    }

    private fun proceedIfReady() {
        if (
            isFinishing ||
                !hasWindowFocus() ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val entry = model.consume() ?: return
        if (entry.hotStart) {
            HotStartAdLog.event("startup_finished destination=caller")
            StartupNavigation.returnToCaller(this, animate = renderer.transitionsEnabled)
        }
        else StartupNavigation.openHome(this, entry, animate = renderer.transitionsEnabled)
    }

    /**
     * 保留原通知权限顺序，结束后在前台 Activity 调用 SDK；失焦可能来自 consent 弹层，不能因此重启协程。
     * ViewModel 仅保留本次调用完成标志，配置重建不重复已完成调用；未完成的旧 Activity 随生命周期取消。
     */
    private fun gatherConsentIfReady() {
        val state = model.state.value
        if (consentStarted || state.consentCompleted || state.consumed || !state.prepared ||
            !state.permissionCompleted || isFinishing || isDestroyed || !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        consentStarted = true
        lifecycleScope.launch {
            Log.d("CleanAds", "Startup consent: started")
            try {
                val consent = GoogleMobileAdsConsentManager.getInstance(applicationContext)
                // 用户要求只等待挂起调用，不读取或根据 allowed 改变广告流程。
                consent.gatherConsent(this@StartupActivity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // SDK 异常结束仍继续既有启动流程；取消不放行，且不将异常或返回值当作用户已同意。
                Log.w("CleanAds", "Startup consent invocation failed", error)
            }
            // 当前 SDK 会捕获内部 CancellationException；销毁后的迟到返回仍须尊重宿主协程取消。
            currentCoroutineContext().ensureActive()
            if (!isFinishing && !isDestroyed) {
                Log.d("CleanAds", "Startup consent: completed")
                model.consentFinished()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (this::renderer.isInitialized) {
            renderer.windowFocusChanged(hasFocus)
            if (this::ads.isInitialized) ads.windowFocusChanged(hasFocus)
            if (hasFocus) {
                gatherConsentIfReady()
                proceedIfReady()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderer.start()
        if (!model.entry().hotStart && model.state.value.prepared && !pushPermissionModel.state.value.completed)
            pushPermission.onResume()
        gatherConsentIfReady()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        pushPermission.drain()
    }

    override fun onPause() {
        renderer.stop()
        super.onPause()
    }

    override fun onDestroy() {
        renderer.dispose()
        super.onDestroy()
    }
}
