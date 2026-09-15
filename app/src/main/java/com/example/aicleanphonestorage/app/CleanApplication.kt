package com.example.aicleanphonestorage.app

import android.app.Application
import com.example.aicleanphonestorage.app.ad.HotStartAdCoordinator
import com.example.aicleanphonestorage.app.ad.AdSdkInitializer
import com.example.aicleanphonestorage.core.diagnostics.PerformanceDiagnostics
import com.example.aicleanphonestorage.core.locale.AppLanguageController
import com.example.aicleanphonestorage.core.locale.LanguageActivityCallbacks
import com.example.aicleanphonestorage.BuildConfig
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSync
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.push.ResidentBadges
import io.docview.push.NotificationRuntime
import io.docview.push.NotificationRuntimeOwner

class CleanApplication : Application(), NotificationRuntimeOwner {
    // 数据源按需初始化；语言偏好仅进行一次异步恢复，不扫描或启动常驻协程。
    val container: AppContainer by lazy { AppContainer(this) }
    internal val languages: AppLanguageController by lazy { AppLanguageController(this) }
    internal val homeCleaning by lazy { HomeCleaningState(BuildConfig.DEFAULT_USER_CHANNEL == "paid") }
    private val notificationHost: CleanNotificationHost by lazy {
        CleanNotificationHost(this, homeCleaning, appCount = { container.installedAppCount.count.value }) {
            notificationRuntime.refreshResident()
        }
    }
    override val notificationRuntime: NotificationRuntime by lazy { NotificationRuntime(this, notificationHost) }

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
        com.example.aicleanphonestorage.core.data.apps.InstalledAppCountMonitor(
            this, container.installedAppCount, notificationRuntime::refreshResident,
        ).start()
        AdSdkInitializer.initialize(this)
        HotStartAdCoordinator(this)
        com.example.aicleanphonestorage.core.analytics.LaunchTelemetry()
        HomeCleaningSync(homeCleaning, { notificationRuntime.refreshResident() }, notificationRuntime::setPaidUser).start()
    }
}
