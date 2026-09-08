package com.example.aicleanphonestorage.app

import android.content.Context
import com.example.aicleanphonestorage.core.data.OneShotTransfer
import com.example.aicleanphonestorage.feature.notifications.data.AndroidNotificationAppsRepository
import com.example.aicleanphonestorage.feature.notifications.data.NotificationRulesStore
import com.example.aicleanphonestorage.feature.notifications.data.NotificationAppsRepository
import com.example.aicleanphonestorage.feature.notifications.data.NotificationCatalog
import com.example.aicleanphonestorage.feature.notifications.service.NotificationListenerConnection
import com.example.aicleanphonestorage.feature.networktraffic.data.AndroidNetworkTrafficRepository
import com.example.aicleanphonestorage.feature.networktraffic.data.NetworkTrafficRepository
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficSnapshotTransfer
import com.example.aicleanphonestorage.core.coroutines.AppDispatchers
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.feature.home.data.EmptyHomeOverviewRepository
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository

/** 手动依赖注入的唯一组装入口。应用级对象禁止保存 Activity/View 或启动隐式后台任务。 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    internal val fileScanRepository by lazy { com.example.aicleanphonestorage.feature.filecleaner.data.FileScanRepository(applicationContext,taskExecutor) }
    internal val fileOperations by lazy { com.example.aicleanphonestorage.feature.filecleaner.operations.FileOperationEngine(applicationContext,fileScanRepository.index,taskExecutor) }
    internal val installedAppsReader by lazy { com.example.aicleanphonestorage.core.data.apps.InstalledAppsReader(applicationContext,taskExecutor) }
    internal val appManagerRepository by lazy { com.example.aicleanphonestorage.feature.appmanager.data.AndroidAppManagerRepository(installedAppsReader) }
    internal val appManagerTransfer by lazy { OneShotTransfer<com.example.aicleanphonestorage.feature.appmanager.data.AppManagerCatalog>() }
    val taskExecutor: TaskExecutor by lazy { TaskExecutor(AppDispatchers()) }
    val notificationRules by lazy { NotificationRulesStore(applicationContext) }
    val notificationConnection by lazy { NotificationListenerConnection() }
    val notificationCatalogTransfer by lazy { OneShotTransfer<NotificationCatalog>() }
    val notificationAppsRepository: NotificationAppsRepository by lazy {
        AndroidNotificationAppsRepository(applicationContext, notificationRules, taskExecutor)
    }
    val trafficSnapshotTransfer: TrafficSnapshotTransfer by lazy { TrafficSnapshotTransfer() }
    val homeOverviewRepository: HomeOverviewRepository by lazy { EmptyHomeOverviewRepository() }
    val networkTrafficRepository: NetworkTrafficRepository by lazy {
        AndroidNetworkTrafficRepository(applicationContext, taskExecutor)
    }
}
