package com.example.aicleanphonestorage.feature.appmanager.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.FeatureExitCoordinator
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ScreenAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.AppManagerCatalog
import kotlinx.coroutines.launch

/** Activity 只处理系统窗口、详情跳转和列表绑定，PackageManager 查询始终由 Repository 执行。 */
class AppManagerActivity : AppCompatActivity() {
    private val container
        get() = (application as CleanApplication).container

    private val model: AppManagerViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppManagerViewModel(
                    container.appManagerRepository,
                    container.appManagerTransfer.take(intent.getLongExtra(EXTRA_CATALOG, 0)),
                )
            }
        }
    }
    private lateinit var binding: ScreenAppManagerBinding
    private lateinit var adapter: AppManagerAdapter
    private var latestState=AppManagerUiState()
    private var committed:AppManagerCatalog?=null
    private var submitted: AppManagerCatalog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        binding = ScreenAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.appManagerTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            binding.appManagerContent.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        val exit = FeatureExitCoordinator(this, { InterstitialPlacements.APPS_EXIT })
        binding.appManagerBack.setOnClickListener { exit.exit() }
        binding.appManagerRetry.setOnClickListener { model.refresh() }
        adapter =
            AppManagerAdapter(lifecycleScope, AppIconLoader(this, container.taskExecutor, 36)) {
                name ->
                if (!AppDetailsSettings.open(this, name))
                    Toast.makeText(
                            this,
                            R.string.app_manager_settings_unavailable,
                            Toast.LENGTH_LONG,
                        )
                        .show()
            }
        binding.appManagerList.layoutManager = LinearLayoutManager(this)
        binding.appManagerList.adapter = adapter
        (binding.appManagerList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations =
            false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    private fun render(state: AppManagerUiState) {
        latestState=state
        state.catalog?.let {
            if (it !== submitted) {
                submitted = it
                val catalog=it
                adapter.submitList(it.apps){committed=catalog;renderListState()}
            }
        }
        renderListState()
    }
    private fun renderListState(){
        val state=latestState
        val empty=state.catalog!=null && committed===state.catalog && adapter.itemCount==0 && !state.refreshing && !state.failed
        binding.appManagerEmpty.isVisible=empty
        binding.appManagerList.isVisible=!empty
        binding.appManagerProgress.isVisible=state.refreshing && adapter.itemCount==0
        binding.appManagerRetry.isVisible=state.failed
    }

    override fun onStart() {
        super.onStart()
        adapter.setActive(true)
    }

    override fun onResume() {
        super.onResume()
        model.onForeground()
    }

    override fun onStop() {
        adapter.setActive(false)
        if (!isChangingConfigurations) model.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        binding.appManagerList.adapter = null
        adapter.setActive(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATALOG = "app_manager.catalog"
    }
}
