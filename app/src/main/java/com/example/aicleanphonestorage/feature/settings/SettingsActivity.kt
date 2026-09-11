package com.example.aicleanphonestorage.feature.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ViewSettingsMenuBinding

/** 固定入口用原生按钮布局；无需列表适配器、后台订阅或额外权限。 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var menu: ViewSettingsMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsPage.install(this, R.string.home_settings)
        // 导航触控区保留 48dp，正文补 12dp，使卡片仍与设计的系统栏下方 60dp 对齐。
        page.settingsBody.setPadding(
            page.settingsBody.paddingLeft,
            (12 * resources.displayMetrics.density).toInt(),
            page.settingsBody.paddingRight,
            page.settingsBody.paddingBottom,
        )
        menu = ViewSettingsMenuBinding.inflate(layoutInflater, page.settingsBody, true)
        menu.settingsNotifications.setOnClickListener {
            com.hjq.permissions.XXPermissions.startPermissionActivity(
                this,
                com.hjq.permissions.permission.PermissionLists.getPostNotificationsPermission(),
            )
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
        // 从当前 Activity 的资源配置取值，跟随系统/应用语言切换及不支持语言的英文回退均与实际 UI 一致。
        menu.settingsLanguage.setValue(getString(R.string.settings_current_language))
        (application as com.example.aicleanphonestorage.app.CleanApplication)
            .notificationRuntime
            .refreshResident()
    }

    private fun open(target: Class<out AppCompatActivity>) {
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
