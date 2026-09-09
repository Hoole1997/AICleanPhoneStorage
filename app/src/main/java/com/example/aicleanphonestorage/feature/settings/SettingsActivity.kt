package com.example.aicleanphonestorage.feature.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewSettingsMenuBinding

/** 固定入口用原生按钮布局；无需列表适配器、后台订阅或额外权限。 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.home_settings)
        val menu = ViewSettingsMenuBinding.inflate(layoutInflater, page.settingsBody, true)
        menu.settingsNotifications.setOnClickListener {
            com.hjq.permissions.XXPermissions.startPermissionActivity(
                this, com.hjq.permissions.permission.PermissionLists.getPostNotificationsPermission())
        }
        menu.settingsLanguage.setOnClickListener { open(LanguageSettingsActivity::class.java) }
        menu.settingsFeedback.setOnClickListener { open(FeedbackActivity::class.java) }
        menu.settingsAbout.setOnClickListener { open(AboutActivity::class.java) }
        menu.settingsPrivacy.setOnClickListener {
            SettingsDestinations.open(
                this,
                SettingsDestinations.privacy(getString(R.string.settings_privacy_url)),
                R.string.settings_link_unavailable,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (application as com.example.aicleanphonestorage.app.CleanApplication).notificationRuntime.refreshResident()
    }

    private fun open(target: Class<out AppCompatActivity>) {
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
