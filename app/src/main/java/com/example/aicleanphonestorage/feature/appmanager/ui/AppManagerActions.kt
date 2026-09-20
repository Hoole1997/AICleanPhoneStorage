package com.example.aicleanphonestorage.feature.appmanager.ui

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.analytics.FeatureTelemetry
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.core.ui.apps.PackageUninstallCoordinator

/** 业务埋点与详情回退属于应用管理；系统卸载确认和待处理请求由公共协调器管理。 */
internal class AppManagerActions(
    private val activity: AppCompatActivity,
    executor: TaskExecutor,
    refresh: () -> Unit,
) {
    private val uninstall = PackageUninstallCoordinator(
        activity, executor, returned = { refresh() },
        unavailable = { packageName, reason ->
            if (reason == PackageUninstallCoordinator.Reason.MISSING) unavailable()
            else openDetails(packageName, true)
        },
        launched = { BusinessTelemetry.emit(MetricEvent.APPS_UNINSTALL_JUMP) },
    )

    fun details(packageName: String) = openDetails(packageName, false)

    private fun openDetails(packageName: String, fromUninstall: Boolean) {
        if (!AppDetailsSettings.open(activity, packageName)) unavailable()
        else if (fromUninstall) BusinessTelemetry.emit(MetricEvent.APPS_UNINSTALL_JUMP)
    }

    fun remove(packageName: String, sizeBytes: Long? = null) {
        if (uninstall.busy) return
        BusinessTelemetry.emit(MetricEvent.APPS_UNINSTALL_CLICK,
            FeatureTelemetry.sizeBand(sizeBytes)?.let { mapOf("size_band" to it) } ?: emptyMap())
        uninstall.remove(packageName)
    }

    private fun unavailable() = Toast.makeText(activity, R.string.app_manager_settings_unavailable, Toast.LENGTH_LONG).show()

    companion object {
        fun uninstallIntent(packageName: String): Intent = PackageUninstallCoordinator.intent(packageName)
    }
}
