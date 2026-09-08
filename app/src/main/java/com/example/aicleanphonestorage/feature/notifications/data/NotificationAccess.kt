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
    fun isGranted():Boolean=com.example.aicleanphonestorage.core.permissions.PermissionChecks.notifications(app,component)
}
