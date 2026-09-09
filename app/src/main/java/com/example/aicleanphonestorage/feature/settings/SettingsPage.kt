package com.example.aicleanphonestorage.feature.settings

import android.graphics.Color
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.aicleanphonestorage.databinding.ScreenSettingsShellBinding

/** 设置功能内复用页面框架，不引入 Activity 基类；背景延伸到系统栏，内容避开系统栏和键盘。 */
internal object SettingsPage {
    fun install(activity: AppCompatActivity, @StringRes title: Int): ScreenSettingsShellBinding {
        activity.enableEdgeToEdge(
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        )
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val binding = ScreenSettingsShellBinding.inflate(activity.layoutInflater)
        activity.setContentView(binding.root)
        binding.settingsTitle.setText(title)
        ViewCompat.setAccessibilityHeading(binding.settingsTitle, true)
        binding.settingsBack.setOnClickListener { activity.finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val safe =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
            binding.settingsContent.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        return binding
    }
}
