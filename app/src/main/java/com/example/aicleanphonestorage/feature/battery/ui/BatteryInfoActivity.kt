package com.example.aicleanphonestorage.feature.battery.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
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
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ScreenBatteryInfoBinding
import kotlinx.coroutines.launch

/** 页面只绑定展示与生命周期；真实数据、格式化和仪表绘制由独立组件承担。 */
class BatteryInfoActivity : AppCompatActivity() {
    private val container
        get() = (application as CleanApplication).container

    private val model: BatteryInfoViewModel by viewModels {
        viewModelFactory {
            initializer {
                BatteryInfoViewModel(
                    container.batteryRepository,
                    container.batteryTransfer.take(intent.getLongExtra(EXTRA_SNAPSHOT, -1)),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val binding = ScreenBatteryInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.batteryTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.batteryContent.setPadding(bars.left, bars.top, bars.right, 0)
            val density = resources.displayMetrics.density
            // 大屏保持 Figma 底部留白，小屏/大字号优先保留内容视口；底部留白含系统导航安全区。
            val compact =
                resources.configuration.screenHeightDp < 800 ||
                    resources.configuration.fontScale >= 1.5f
            binding.batteryFooter.setPadding(
                0,
                (36 * density).toInt(),
                0,
                maxOf(bars.bottom, ((if (compact) 24 else 100) * density).toInt()),
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.batteryBack.setOnClickListener { finish() }
        binding.batteryDone.setOnClickListener { finish() }
        val renderer = BatteryInfoRenderer(binding)
        renderer.render(model.state.value)
        lifecycleScope.launch {
            // 页面失去前台时立刻取消冷流订阅，广播/ContentObserver 在 awaitClose 中解除。
            repeatOnLifecycle(Lifecycle.State.RESUMED) { model.state.collect(renderer::render) }
        }
        com.example.aicleanphonestorage.core.analytics.PageTelemetry.attach(this, "battery")
    }

    companion object {
        const val EXTRA_SNAPSHOT = "battery.snapshot"
    }
}
