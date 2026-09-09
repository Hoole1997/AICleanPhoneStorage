package com.example.aicleanphonestorage.feature.push

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.core.permissions.PermissionCoordinator
import com.example.aicleanphonestorage.core.permissions.PermissionKind
import com.remax.notification.NotificationRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 复用统一说明 UI 和系统回调；首次只解释一次，拒绝后用户可从设置页再次管理。 */
internal class PushPermissionCoordinator(
    private val activity: AppCompatActivity,
    private val permissions: PermissionCoordinator,
    private val runtime: NotificationRuntime,
) {
    private var checking = false
    init {
        permissions.register(ROUTE, before = {}) { runtime.refreshResident() }
    }

    fun onResume(allowPrompt: () -> Boolean) {
        runtime.refreshResident()
        if (!allowPrompt() || checking || Build.VERSION.SDK_INT < 33 || permissions.pending ||
            NotificationManagerCompat.from(activity).areNotificationsEnabled()) return
        checking = true
        activity.lifecycleScope.launch {
            try {
                val shown = withContext(Dispatchers.IO) {
                    activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean("explained", false)
                }
                if (shown || !allowPrompt() || permissions.pending || activity.supportFragmentManager.isStateSaved ||
                    !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                permissions.rationale(ROUTE, PermissionKind.POST_NOTIFICATIONS)
                withContext(Dispatchers.IO) {
                    activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean("explained", true).commit()
                }
            } finally { checking = false }
        }
    }

    companion object {
        private const val ROUTE = "permission.push"
        private const val PREFS = "push_permission"
    }
}
