package com.example.aicleanphonestorage.feature.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.applovin.sdk.AppLovinSdk
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewAdDebugBinding
import com.example.aicleanphonestorage.feature.settings.addebug.AdDebugState
import com.example.aicleanphonestorage.feature.settings.addebug.AdDebugViewModel
import com.example.aicleanphonestorage.feature.settings.addebug.DebugAdFormat
import kotlinx.coroutines.launch

/** 仅 debug 源集打包；工具页管理设置，独立预览 Activity 为 SDK 提供明确的销毁边界。 */
class AdDebugActivity : AppCompatActivity() {
    private val model: AdDebugViewModel by viewModels { AdDebugViewModel.factory(application) }
    private lateinit var binding: ViewAdDebugBinding
    private var rendering = false
    private val showPreview = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val message = result.data?.getStringExtra(AdDebugPreviewActivity.EXTRA_RESULT)
        if (result.resultCode == Activity.RESULT_OK && !message.isNullOrBlank()) {
            model.finishShow(message.take(4_096))
        } else {
            model.cancelShow()
        }
    }

    private val sources = arrayOf(
        AdSource.BIDDING, AdSource.ADMOB, AdSource.GAM,
        AdSource.PANGLE, AdSource.TOPON, AdSource.MAX,
    )
    private val formats = arrayOf(
        DebugAdFormat.APP_OPEN, DebugAdFormat.INTERSTITIAL, DebugAdFormat.REWARDED,
        DebugAdFormat.FULL_SCREEN_NATIVE, DebugAdFormat.NATIVE, DebugAdFormat.BANNER,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.settings_ad_debug)
        binding = ViewAdDebugBinding.inflate(layoutInflater, page.settingsBody, true)
        ViewCompat.setAccessibilityHeading(binding.adDebugControlsHeading, true)
        ViewCompat.setAccessibilityHeading(binding.adDebugDiagnosticsHeading, true)
        ViewCompat.setAccessibilityHeading(binding.adDebugResultHeading, true)
        installControls()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun installControls() {
        binding.adDebugSource.adapter = ArrayAdapter(
            this, R.layout.debug_ad_spinner_item, sources.map { it.name },
        ).apply { setDropDownViewResource(R.layout.debug_ad_spinner_dropdown_item) }
        binding.adDebugFormat.adapter = ArrayAdapter(
            this, R.layout.debug_ad_spinner_item, formats.map { formatLabel(it) },
        ).apply { setDropDownViewResource(R.layout.debug_ad_spinner_dropdown_item) }
        binding.adDebugSource.onItemSelectedListener = selectionListener { position ->
            val selected = sources[position]
            if (canEdit() && selected != model.state.value.source) {
                model.selectSource(selected)
            }
        }
        binding.adDebugFormat.onItemSelectedListener = selectionListener { position ->
            val selected = formats[position]
            if (canEdit() && selected != model.state.value.format) {
                model.selectFormat(selected)
            }
        }
        binding.adDebugGlobalEnabled.setOnCheckedChangeListener { _, checked ->
            if (canEdit() && checked != model.state.value.globalEnabled) {
                model.setGlobalEnabled(checked)
            }
        }
        binding.adDebugShow.setOnClickListener { showAd() }
        binding.adDebugRefresh.setOnClickListener { model.refresh() }
        binding.adDebugReset.setOnClickListener {
            model.resetDefaults()
        }
        binding.adDebugClear.setOnClickListener {
            model.finishShow(getString(R.string.ad_debug_result_cleared))
        }
        binding.adDebugMaxDebugger.setOnClickListener {
            try {
                AppLovinSdk.getInstance(this).showMediationDebugger()
            } catch (error: Exception) {
                model.finishShow(errorMessage(error))
            }
        }
    }

    private fun render(state: AdDebugState) {
        rendering = true
        binding.adDebugSource.setSelection(sources.indexOf(state.source), false)
        binding.adDebugFormat.setSelection(formats.indexOf(state.format), false)
        binding.adDebugGlobalEnabled.isChecked = state.globalEnabled
        rendering = false

        val editable = !state.loading && !state.busy
        binding.adDebugGlobalEnabled.isEnabled = editable
        binding.adDebugSource.isEnabled = editable
        binding.adDebugFormat.isEnabled = editable
        binding.adDebugShow.isEnabled = editable && state.globalEnabled && state.sdkReady
        binding.adDebugRefresh.isEnabled = editable
        binding.adDebugReset.isEnabled = editable
        binding.adDebugClear.isEnabled = editable
        binding.adDebugMaxDebugger.isEnabled = editable && state.sdkReady
        binding.adDebugProgress.isVisible = state.loading || state.busy
        binding.adDebugShow.setText(
            if (state.busy) R.string.ad_debug_showing else R.string.ad_debug_show,
        )
        binding.adDebugDiagnostics.text = state.diagnostics
        binding.adDebugResult.text = state.result.ifBlank { getString(R.string.ad_debug_no_result) }
    }

    private fun showAd() {
        if (!model.beginShow()) return
        try {
            showPreview.launch(AdDebugPreviewActivity.intent(this, model.state.value.format))
        } catch (error: Exception) {
            model.finishShow(errorMessage(error))
        }
    }

    private fun canEdit(): Boolean =
        !rendering && !model.state.value.loading && !model.state.value.busy

    private fun errorMessage(error: Exception): String = getString(
        R.string.ad_debug_error, error.message ?: error.javaClass.simpleName,
    )

    private fun formatLabel(format: DebugAdFormat): String = getString(
        when (format) {
            DebugAdFormat.APP_OPEN -> R.string.ad_debug_format_app_open
            DebugAdFormat.INTERSTITIAL -> R.string.ad_debug_format_interstitial
            DebugAdFormat.REWARDED -> R.string.ad_debug_format_rewarded
            DebugAdFormat.FULL_SCREEN_NATIVE -> R.string.ad_debug_format_full_screen_native
            DebugAdFormat.NATIVE -> R.string.ad_debug_format_native
            DebugAdFormat.BANNER -> R.string.ad_debug_format_banner
        },
    )

    private fun selectionListener(select: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position >= 0) select(position)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
