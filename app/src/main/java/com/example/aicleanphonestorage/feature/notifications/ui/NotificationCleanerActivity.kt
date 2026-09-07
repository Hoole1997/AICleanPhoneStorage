package com.example.aicleanphonestorage.feature.notifications.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ScreenNotificationCleanerBinding
import kotlinx.coroutines.launch

class NotificationCleanerActivity : AppCompatActivity() {
    private val viewModel: NotificationCleanerViewModel by viewModels {
        viewModelFactory { initializer {
            val container = (application as CleanApplication).container
            NotificationCleanerViewModel(container.notificationAppsRepository, container.notificationConnection.connected, createSavedStateHandle(), entry = false,
                initialCatalog = container.notificationCatalogTransfer.take(intent.getLongExtra(EXTRA_CATALOG, 0)))
        } }
    }
    private lateinit var renderer: NotificationCleanerRenderer
    private var returning = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK))
        val binding = ScreenNotificationCleanerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.notificationTitle, true)
        binding.notificationBack.setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.notificationContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        renderer = NotificationCleanerRenderer(binding, lifecycleScope, AppIconLoader(this, (application as CleanApplication).container.taskExecutor),
            viewModel::setEnabled, { viewModel.onForeground(); viewModel.refresh() }) {
            if (!viewModel.state.value.rulesLoaded) { viewModel.onForeground(); viewModel.refresh() }
            else if (!NotificationAccessSettings.open(this)) Toast.makeText(this, R.string.notification_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) } }
    }
    override fun onStart() { super.onStart(); renderer.setActive(true) }
    override fun onResume() { super.onResume(); viewModel.onForeground() }
    override fun onResumeFragments() { super.onResumeFragments(); render(viewModel.state.value) }
    override fun onStop() { renderer.setActive(false); if (!isChangingConfigurations) viewModel.onBackground(); super.onStop() }
    override fun onDestroy() { renderer.dispose(); super.onDestroy() }
    private fun render(state: NotificationUiState) {
        renderer.render(state)
        if (state.phase == NotificationPhase.NeedsAccess && !returning && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            returning = true
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_REENTER, true))
            finish()
        }
    }
    companion object {
        const val EXTRA_CATALOG = "notification_entry.catalog"
        const val EXTRA_REENTER = "notification_entry.reenter"
    }
}
