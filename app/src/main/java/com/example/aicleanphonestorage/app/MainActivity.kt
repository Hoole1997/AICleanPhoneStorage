package com.example.aicleanphonestorage.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.ui.HomeViewModel
import com.example.aicleanphonestorage.feature.home.ui.renderHome
import kotlinx.coroutines.launch

/** Activity 只负责窗口、依赖组装和生命周期，不读取存储、不执行扫描。 */
class MainActivity : AppCompatActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer { HomeViewModel((application as CleanApplication).container.homeOverviewRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Binding 只属于本次 Activity 实例；不要传入 ViewModel、Repository 或应用容器。
        val binding = ScreenHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.pageTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.retryButton.setOnClickListener { homeViewModel.retry() }

        lifecycleScope.launch {
            // STOPPED 时取消收集，销毁时整个 Scope 取消；旋转由 ViewModelStore 保留状态持有者。
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { binding.renderHome(it) }
            }
        }
    }
}
