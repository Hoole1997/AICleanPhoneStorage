package com.example.aicleanphonestorage.feature.notifications.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.example.aicleanphonestorage.app.ad.NativeAdCoordinator
import com.example.aicleanphonestorage.app.ad.NativeAdFeature
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.app.ad.FeatureExitCoordinator
import com.example.aicleanphonestorage.app.ad.HomeExitAdContract
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.core.ui.completion.CompletionContract
import com.example.aicleanphonestorage.databinding.ScreenNotificationCleanerBinding
import kotlinx.coroutines.launch

class NotificationCleanerActivity : AppCompatActivity() {
    private val viewModel: NotificationCleanerViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as CleanApplication).container
                NotificationCleanerViewModel(
                    container.notificationAppsRepository,
                    container.notificationConnection.connected,
                    createSavedStateHandle(),
                    entry = false,
                    initialCatalog =
                        container.notificationCatalogTransfer.take(
                            intent.getLongExtra(EXTRA_CATALOG, 0)
                        ),
                )
            }
        }
    }
    private lateinit var renderer: NotificationCleanerRenderer
    private var returning = false
    private val completion =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (
                result.data?.getStringExtra(CompletionContract.ACTION) ==
                    CompletionContract.CONTINUE
            ) {
                startActivity(
                    HomeExitAdContract.intent(this, InterstitialPlacements.NOTIFICATIONS_COMPLETE_EXIT)
                )
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        val binding = ScreenNotificationCleanerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NativeAdCoordinator(this, binding.nativeAd, NativeAdFeature.NOTIFY.featureSlot)
        ViewCompat.setAccessibilityHeading(binding.notificationTitle, true)
        val exit = FeatureExitCoordinator(this, { InterstitialPlacements.NOTIFICATIONS_EXIT })
        binding.notificationBack.setOnClickListener { exit.exit() }
        binding.notificationDone.setOnClickListener {
            viewModel.completionReport()?.let { report ->
                completion.launch(CompletionContract.intent(this, report))
                viewModel.completionPresented()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.notificationContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        renderer =
            NotificationCleanerRenderer(
                binding,
                lifecycleScope,
                AppIconLoader(this, (application as CleanApplication).container.taskExecutor),
                viewModel::setEnabled,
                {
                    viewModel.onForeground()
                    viewModel.refresh()
                },
            ) {
                if (!viewModel.state.value.rulesLoaded) {
                    viewModel.onForeground()
                    viewModel.refresh()
                } else
                    lifecycleScope.launch {
                        val granted =
                            (application as CleanApplication)
                                .container
                                .notificationAppsRepository
                                .hasAccess()
                        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                            return@launch
                        if (granted) {
                            android.service.notification.NotificationListenerService.requestRebind(
                                com.example.aicleanphonestorage.feature.notifications.data
                                    .NotificationAccess(this@NotificationCleanerActivity)
                                    .component
                            )
                            viewModel.onForeground()
                        } else returnToEntry()
                    }
            }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    override fun onStart() {
        super.onStart()
        renderer.setActive(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        render(viewModel.state.value)
    }

    override fun onStop() {
        renderer.setActive(false)
        if (!isChangingConfigurations) viewModel.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        renderer.dispose()
        super.onDestroy()
    }

    private fun render(state: NotificationUiState) {
        renderer.render(state)
        if (
            state.phase == NotificationPhase.NeedsAccess &&
                !returning &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            returnToEntry()
        }
    }

    private fun returnToEntry() {
        if (returning) return
        returning = true
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_REENTER, true)
        )
        finish()
    }

    companion object {
        const val EXTRA_CATALOG = "notification_entry.catalog"
        const val EXTRA_REENTER = "notification_entry.reenter"
    }
}
