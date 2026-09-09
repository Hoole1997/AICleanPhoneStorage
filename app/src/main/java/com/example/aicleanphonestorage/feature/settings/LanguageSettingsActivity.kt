package com.example.aicleanphonestorage.feature.settings

import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.example.aicleanphonestorage.core.locale.AppLanguages
import com.example.aicleanphonestorage.databinding.ViewLanguagePickerBinding
import java.util.Locale
import kotlinx.coroutines.launch

class LanguageSettingsActivity : AppCompatActivity() {
    private val model: LanguageSettingsViewModel by viewModels {
        viewModelFactory {
            initializer { LanguageSettingsViewModel((application as CleanApplication).languages) }
        }
    }
    private lateinit var binding: ViewLanguagePickerBinding
    private lateinit var adapter: LanguageOptionsAdapter
    private var lastError = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastError = savedInstanceState?.getLong("language.error") ?: 0
        val page = SettingsPage.install(this, R.string.settings_language)
        // RecyclerView 是唯一滚动容器，避免嵌入设置页的 ScrollView 后全量测量列表。
        page.settingsContent.removeView(page.settingsScroll)
        binding = ViewLanguagePickerBinding.inflate(layoutInflater, page.settingsContent, true)
        adapter = LanguageOptionsAdapter(model::select)
        binding.languageList.layoutManager = LinearLayoutManager(this)
        binding.languageList.adapter = adapter
        (binding.languageList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun render(state: LanguageSelectionState) {
        val display = resources.configuration.locales[0]
        val system = if (android.os.Build.VERSION.SDK_INT >= 33)
            getSystemService(android.app.LocaleManager::class.java).systemLocales[0]
        else Resources.getSystem().configuration.locales[0]
        val rows =
            listOf(
                LanguageRow(
                    "",
                    getString(R.string.language_follow_system),
                    getString(R.string.language_system_description, system.getDisplayName(display)),
                    state.selected.isEmpty(),
                    !state.saving,
                )
            ) +
                AppLanguages.supported.map {
                    LanguageRow(
                        it.tag,
                        it.nativeName,
                        Locale.forLanguageTag(it.tag).getDisplayName(display),
                        it.tag == state.selected,
                        !state.saving,
                    )
                }
        adapter.submitList(rows)
        binding.languageProgress.isVisible = state.saving
        if (state.error > lastError) {
            lastError = state.error
            Toast.makeText(this, R.string.language_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putLong("language.error", lastError)
        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        binding.languageList.adapter = null
        super.onDestroy()
    }
}
