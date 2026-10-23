package com.example.aicleanphonestorage.feature.retention

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard

internal class RetentionSettingsState(private val saved: SavedStateHandle) : ViewModel() {
    var pending: Boolean
        get() = saved["retention.system.pending"] ?: false
        set(value) { saved["retention.system.pending"] = value }
}

/** 原因页只进入本应用的系统应用信息页，由用户在系统页自行操作；不直接请求卸载。 */
internal class RetentionSettingsCoordinator(private val activity: AppCompatActivity, private val changed: (Boolean) -> Unit) {
    private val state = ViewModelProvider(activity, viewModelFactory {
        initializer { RetentionSettingsState(createSavedStateHandle()) }
    })[RetentionSettingsState::class.java]
    private var guard: AutoCloseable? = null
    val busy get() = state.pending
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 系统应用信息页不提供卸载结果；返回后留在原因页，释放防连点与热启动广告保护。
        state.pending = false
        release()
        changed(false)
    }

    init {
        if (state.pending) guard = ForegroundTransitionGuard.hold("retention_app_settings")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = release()
        })
    }

    fun launch() {
        if (busy || !activity.hasWindowFocus() || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        state.pending = true
        guard = ForegroundTransitionGuard.hold("retention_app_settings")
        changed(false)
        try { launcher.launch(intent(activity.packageName)) }
        catch (_: android.content.ActivityNotFoundException) { failed() }
        catch (_: SecurityException) { failed() }
    }

    private fun failed() { state.pending = false; release(); changed(true) }
    private fun release() { guard?.close(); guard = null }

    companion object {
        fun intent(ownPackage: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ownPackage, null))
    }
}
