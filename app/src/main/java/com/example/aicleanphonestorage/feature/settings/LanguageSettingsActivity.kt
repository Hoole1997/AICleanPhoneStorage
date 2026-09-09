package com.example.aicleanphonestorage.feature.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewSettingsInfoBinding

/** 按当前范围只保留入口，尚未提供语言列表，不改应用或系统 Locale。 */
class LanguageSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.settings_language)
        val binding = ViewSettingsInfoBinding.inflate(layoutInflater, page.settingsBody, true)
        binding.settingsInfoHeading.setText(R.string.settings_language_pending_title)
        binding.settingsInfoMessage.setText(R.string.settings_language_pending_message)
        ViewCompat.setAccessibilityHeading(binding.settingsInfoHeading, true)
    }
}
