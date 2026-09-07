package com.example.aicleanphonestorage.feature.notifications.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.aicleanphonestorage.feature.notifications.service.NotificationCleanerService

class NotificationAccess(context: Context) {
    private val app = context.applicationContext
    val component = ComponentName(app, NotificationCleanerService::class.java)
    fun isGranted(): Boolean = if (Build.VERSION.SDK_INT >= 27) {
        app.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component) == true
    } else NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
}
