package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.core.permissions.PermissionCoordinator
import com.example.aicleanphonestorage.feature.appmanager.ui.AppManagerEntryViewModel
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.ui.CleanupEntryViewModel
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficViewModel
import com.example.aicleanphonestorage.feature.notifications.ui.NotificationCleanerViewModel

/** 首页点击与通知入口共享互斥规则；先取消旧入口，再启动一个功能，避免重复授权/扫描。 */
internal class HomeEntryActions(
    private val permissions: PermissionCoordinator,
    private val traffic: NetworkTrafficViewModel,
    private val notifications: NotificationCleanerViewModel,
    private val cleanup: CleanupEntryViewModel,
    private val apps: AppManagerEntryViewModel,
    private val beforeNavigation: () -> Unit,
    private val openSettings: () -> Unit,
) : HomeUiActions {
    fun cancelPending() {
        beforeNavigation()
        permissions.cancel()
        traffic.cancelEntry()
        notifications.cancelEntry()
        cleanup.cancel()
        apps.cancel()
    }

    override fun onSettings() { cancelPending(); openSettings() }
    override fun onSmartClean() { cancelPending(); cleanup.begin(CleanupFeature.SMART_CLEAN) }
    override fun onToolSelected(tool: HomeTool) {
        cancelPending()
        when (tool) {
            HomeTool.Network -> traffic.beginEntry()
            HomeTool.Notifications -> notifications.beginEntry()
            HomeTool.Apps -> apps.begin()
            HomeTool.Compress -> cleanup.begin(CleanupFeature.PHOTO_COMPRESS)
            HomeTool.LargeFiles -> cleanup.begin(CleanupFeature.LARGE_FILES)
            HomeTool.UnusedFiles -> cleanup.begin(CleanupFeature.UNUSED_FILES)
            HomeTool.Screenshots -> cleanup.begin(CleanupFeature.SCREENSHOTS)
        }
    }
}
