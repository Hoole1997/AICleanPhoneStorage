package com.example.aicleanphonestorage.feature.appmanager.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 只发起系统卸载确认，不乐观删除列表；取消/成功均重新读取系统事实。 */
internal class AppManagerActions(
    private val activity: AppCompatActivity,
    private val executor: TaskExecutor,
    private val refresh: () -> Unit,
) {
    private var checking: Job? = null
    private var awaitingResult = false
    private val uninstall =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            awaitingResult = false
            refresh()
        }

    fun details(packageName: String) {
        if (!AppDetailsSettings.open(activity, packageName)) unavailable()
    }

    fun remove(packageName: String) {
        if (checking?.isActive == true || awaitingResult) return
        checking =
            activity.lifecycleScope.launch {
                // 点击时重新验证应用仍在且允许卸载，扫描结果不能作为删除授权。
                val canUninstall =
                    executor.io {
                        try {
                            @Suppress("DEPRECATION")
                            val info = activity.packageManager.getApplicationInfo(packageName, 0)
                            packageName != activity.packageName &&
                                info.flags and
                                    (ApplicationInfo.FLAG_SYSTEM or
                                        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    return@launch
                when (canUninstall) {
                    null -> {
                        unavailable()
                        refresh()
                    }
                    false -> details(packageName)
                    true ->
                        try {
                            awaitingResult = true
                            uninstall.launch(uninstallIntent(packageName))
                        } catch (_: ActivityNotFoundException) {
                            awaitingResult = false
                            details(packageName)
                        } catch (_: SecurityException) {
                            awaitingResult = false
                            details(packageName)
                        }
                }
            }
    }

    private fun unavailable() =
        Toast.makeText(activity, R.string.app_manager_settings_unavailable, Toast.LENGTH_LONG)
            .show()

    companion object {
        fun uninstallIntent(packageName: String): Intent =
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
