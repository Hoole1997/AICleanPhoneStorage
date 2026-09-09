package com.example.aicleanphonestorage.feature.startup

import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import io.docview.push.NotificationDestination

/** 启动页只转交白名单目标与一个调试标记，绝不复制通知正文、任意 URI 或完整 extras。 */
internal object StartupNavigation {
    const val COMPLETED = "startup.completed"

    fun read(intent: Intent) = StartupEntry(
        NotificationNavigation.read(intent) ?: NotificationDestination.HOME,
        HomePreviewSupport.initialSelection(intent, null),
    )

    fun needsStartup(intent: Intent) = !intent.getBooleanExtra(COMPLETED, false) && NotificationNavigation.read(intent) != null

    fun startupIntent(context: Context, entry: StartupEntry) =
        Intent(context, StartupActivity::class.java)
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, entry.destination.key)
            .putExtra(HomePreviewSupport.EXTRA_MODE, entry.previewMode)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun homeIntent(context: Context, entry: StartupEntry) =
        Intent(context, MainActivity::class.java)
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, entry.destination.key)
            .putExtra(HomePreviewSupport.EXTRA_MODE, entry.previewMode)
            .putExtra(COMPLETED, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
