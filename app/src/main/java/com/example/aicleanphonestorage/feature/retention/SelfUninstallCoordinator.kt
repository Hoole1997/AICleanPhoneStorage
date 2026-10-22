package com.example.aicleanphonestorage.feature.retention

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard

internal class SelfUninstallState(private val saved: SavedStateHandle) : ViewModel() {
    var pending: Boolean
        get() = saved["retention.system.pending"] ?: false
        set(value) { saved["retention.system.pending"] = value }
}

/** 专用自卸载入口只允许当前应用包名，不能削弱应用管理/病毒页面原有的自家包保护规则。 */
internal class SelfUninstallCoordinator(private val activity: AppCompatActivity, private val changed: (Boolean) -> Unit) {
    private val state = ViewModelProvider(activity, viewModelFactory {
        initializer { SelfUninstallState(createSavedStateHandle()) }
    })[SelfUninstallState::class.java]
    private var guard: AutoCloseable? = null
    val busy get() = state.pending
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 系统返回不等于卸载成功；应用仍活着时留在原因页，让用户自由保留或再次操作。
        state.pending = false
        release()
        changed(false)
    }

    init {
        if (state.pending) guard = ForegroundTransitionGuard.hold("self_uninstall")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = release()
        })
    }

    fun launch() {
        if (busy || !activity.hasWindowFocus() || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        state.pending = true
        guard = ForegroundTransitionGuard.hold("self_uninstall")
        changed(false)
        try { launcher.launch(intent(activity.packageName)) }
        catch (_: android.content.ActivityNotFoundException) { failed() }
        catch (_: SecurityException) { failed() }
    }

    private fun failed() { state.pending = false; release(); changed(true) }
    private fun release() { guard?.close(); guard = null }

    companion object {
        fun intent(ownPackage: String) = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", ownPackage, null))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
