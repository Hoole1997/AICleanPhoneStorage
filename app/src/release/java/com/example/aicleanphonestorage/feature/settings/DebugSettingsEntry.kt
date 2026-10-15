package com.example.aicleanphonestorage.feature.settings

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

/** 发布包没有调试 Activity/资源，保留单一静态装配边界，避免引用不存在的 debug 类。 */
internal object DebugSettingsEntry {
    @Suppress("UNUSED_PARAMETER")
    fun install(activity: AppCompatActivity, body: ViewGroup) = Unit
}
