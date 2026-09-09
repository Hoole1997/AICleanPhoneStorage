package com.example.aicleanphonestorage.app

import android.app.Application
import com.example.aicleanphonestorage.core.diagnostics.PerformanceDiagnostics
import com.example.aicleanphonestorage.core.locale.AppLanguageController
import com.example.aicleanphonestorage.core.locale.LanguageActivityCallbacks
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.push.ResidentBadges
import io.docview.push.NotificationRuntime
import io.docview.push.NotificationRuntimeOwner

class CleanApplication : Application(), NotificationRuntimeOwner {
    // 数据源按需初始化；语言偏好仅进行一次异步恢复，不扫描或启动常驻协程。
    val container: AppContainer by lazy { AppContainer(this) }
    internal val languages: AppLanguageController by lazy { AppLanguageController(this) }
    private val notificationHost by lazy { CleanNotificationHost(this) }
    override val notificationRuntime by lazy { NotificationRuntime(this, notificationHost) }

    internal fun updateResidentBadges(badges: ResidentBadges) {
        notificationHost.updateBadges(badges)
        notificationRuntime.refreshResident()
    }

    override fun onCreate() {
        super.onCreate()
        PerformanceDiagnostics.install()
        registerActivityLifecycleCallbacks(LanguageActivityCallbacks(languages))
        languages.initialize()
        notificationRuntime.initialize()
    }
}
