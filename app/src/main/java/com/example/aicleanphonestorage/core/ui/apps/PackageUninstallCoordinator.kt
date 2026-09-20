package com.example.aicleanphonestorage.core.ui.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.core.coroutines.TaskExecutor
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class UninstallRequestState(private val saved: SavedStateHandle) : ViewModel() {
    var packageName: String?
        get() = saved["uninstall.package"]
        set(value) { saved["uninstall.package"] = value }
    fun take(): String? = packageName.also { packageName = null }
}

/** 两个功能共用系统卸载入口；只保存包名，结果回调不代表卸载成功，由业务层重新查询系统事实。 */
internal class PackageUninstallCoordinator(
    private val activity: AppCompatActivity,
    private val executor: TaskExecutor,
    private val returned: (String) -> Unit,
    private val unavailable: (String, Reason) -> Unit,
    private val launched: () -> Unit = {},
) {
    enum class Reason { MISSING, PROTECTED, UNAVAILABLE }
    private val app = activity.applicationContext
    private val pending = ViewModelProvider(activity, viewModelFactory {
        initializer { UninstallRequestState(createSavedStateHandle()) }
    })[UninstallRequestState::class.java]
    private var checking: Job? = null
    private var externalUi: AutoCloseable? = null
    val busy: Boolean get() = checking?.isActive == true || pending.packageName != null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val packageName = pending.take()
        releaseGuard()
        if (packageName != null) returned(packageName)
    }

    init {
        if (pending.packageName != null) externalUi = ForegroundTransitionGuard.hold("uninstall")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = releaseGuard()
        })
    }

    fun remove(packageName: String) {
        if (busy || packageName.isBlank()) return
        checking = activity.lifecycleScope.launch {
            val reason = executor.io {
                try {
                    @Suppress("DEPRECATION")
                    val info = app.packageManager.getApplicationInfo(packageName, 0)
                    if (packageName == app.packageName || info.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) Reason.PROTECTED else null
                } catch (_: PackageManager.NameNotFoundException) { Reason.MISSING }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: RuntimeException) { Reason.UNAVAILABLE }
            }
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (reason != null) {
                unavailable(packageName, reason)
                if (reason == Reason.MISSING) returned(packageName)
                return@launch
            }
            try {
                pending.packageName = packageName
                releaseGuard()
                externalUi = ForegroundTransitionGuard.hold("uninstall")
                launcher.launch(intent(packageName))
                launched()
            } catch (_: ActivityNotFoundException) {
                pending.take(); releaseGuard(); unavailable(packageName, Reason.UNAVAILABLE)
            } catch (_: SecurityException) {
                pending.take(); releaseGuard(); unavailable(packageName, Reason.UNAVAILABLE)
            }
        }
    }

    private fun releaseGuard() { externalUi?.close(); externalUi = null }

    companion object {
        fun intent(packageName: String): Intent =
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
