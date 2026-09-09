package com.example.aicleanphonestorage.feature.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ViewSettingsInfoBinding
import kotlinx.coroutines.launch

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.settings_about)
        val binding = ViewSettingsInfoBinding.inflate(layoutInflater, page.settingsBody, true)
        binding.settingsInfoImage.isVisible = true
        binding.settingsInfoHeading.setText(R.string.app_name)
        binding.settingsInfoMessage.setText(R.string.settings_about_description)
        ViewCompat.setAccessibilityHeading(binding.settingsInfoHeading, true)
        val app = application as CleanApplication
        // PackageManager 查询离开主线程，任务归页面生命周期；不缓存 Activity 或启动常驻任务。
        lifecycleScope.launch {
            val version =
                app.container.taskExecutor.io {
                    try {
                        @Suppress("DEPRECATION")
                        val info = app.packageManager.getPackageInfo(app.packageName, 0)
                        info.versionName.orEmpty() to PackageInfoCompat.getLongVersionCode(info)
                    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                        null
                    }
                }
            binding.settingsInfoVersion.text =
                version
                    ?.takeIf { it.first.isNotBlank() }
                    ?.let { getString(R.string.settings_version, it.first, it.second) }
                    ?: getString(R.string.settings_version_unavailable)
            binding.settingsInfoVersion.isVisible = true
        }
    }
}
