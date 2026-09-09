package com.example.aicleanphonestorage.app

import android.app.Application
import com.example.aicleanphonestorage.core.diagnostics.PerformanceDiagnostics
import com.example.aicleanphonestorage.core.locale.AppLanguageController
import com.example.aicleanphonestorage.core.locale.LanguageActivityCallbacks

class CleanApplication : Application() {
    // 数据源按需初始化；语言偏好仅进行一次异步恢复，不扫描或启动常驻协程。
    val container: AppContainer by lazy { AppContainer(this) }
    internal val languages: AppLanguageController by lazy { AppLanguageController(this) }

    override fun onCreate() {
        super.onCreate()
        PerformanceDiagnostics.install()
        registerActivityLifecycleCallbacks(LanguageActivityCallbacks(languages))
        languages.initialize()
    }
}
