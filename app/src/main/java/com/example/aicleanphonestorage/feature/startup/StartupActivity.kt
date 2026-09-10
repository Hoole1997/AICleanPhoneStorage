package com.example.aicleanphonestorage.feature.startup

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.aicleanphonestorage.R

/** 独立原生启动 Activity；广告回调放行后统一进入首页，再由首页处理通知目的地。 */
class StartupActivity : AppCompatActivity() {
    private val model: StartupViewModel by viewModels {
        viewModelFactory {
            initializer {
                val languages = (application as CleanApplication).languages
                StartupViewModel(createSavedStateHandle(), prepare = { languages.ready.await() })
            }
        }
    }
    private lateinit var renderer: StartupRenderer
    private lateinit var ads: StartupAdCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val incoming = StartupNavigation.read(intent)
        // 在创建 SavedStateHandle 前规范化 Intent，避免把通知正文或外部同名状态键存入页面状态。
        setIntent(StartupNavigation.startupIntent(this, incoming))
        val binding = ScreenStartupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderer = StartupRenderer(binding)
        lifecycleScope.launch {
            val appResources = applicationContext.resources
            val image = withContext(Dispatchers.IO) { appResources.getDrawable(R.drawable.startup_background, null) }
            binding.startupBackground.setImageDrawable(image)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            renderer.insets(insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        if (!model.hasEntry()) model.accept(incoming)
        ads = StartupAdCoordinator(this, binding.root, model)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    renderer.render(state)
                    if (state.consumed && !isFinishing) finish()
                    proceedIfReady()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = StartupNavigation.read(intent)
        setIntent(StartupNavigation.startupIntent(this, incoming))
        model.accept(incoming)
        proceedIfReady()
    }

    private fun proceedIfReady() {
        if (isFinishing || !hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val entry = model.consume() ?: return
        StartupNavigation.openHome(this, entry, animate = renderer.transitionsEnabled)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (this::renderer.isInitialized) {
            renderer.windowFocusChanged(hasFocus)
            if (this::ads.isInitialized) ads.windowFocusChanged(hasFocus)
            if (hasFocus) proceedIfReady()
        }
    }

    override fun onResume() {
        super.onResume()
        renderer.start()
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
