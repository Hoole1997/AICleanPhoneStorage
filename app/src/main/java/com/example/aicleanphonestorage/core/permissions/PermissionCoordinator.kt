package com.example.aicleanphonestorage.core.permissions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlinx.coroutines.launch

internal data class PermissionOutcome(
    val kind: PermissionKind,
    val granted: Boolean,
    val directory: String? = null,
    val unavailable: Boolean = false,
    val cancelled: Boolean = false,
    val skipped: Boolean = false,
)

/** 一个申请页共用一个协调器，回调仅存于 Activity；监测事实在 ViewModel，业务续接只在 resumed 执行。 */
internal class PermissionCoordinator(
    private val activity: AppCompatActivity,
    private val model: PermissionFlowViewModel,
    private val access: AndroidPermissionAccess,
) {
    private data class Handler(
        val before: (PermissionKind) -> Unit,
        val result: (PermissionOutcome) -> Unit,
        val launch: (PermissionKind) -> Unit,
    )

    private val handlers = mutableMapOf<String, Handler>()
    private var away = false
    private var externalUi: AutoCloseable? = null
    private val runtime =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            releaseExternalUi()
            model.runtimeResult()
        }
    private val directory =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            releaseExternalUi()
            model.directoryResult(it?.toString())
        }
    val pending: Boolean
        get() = model.pending

    init {
        activity.supportFragmentManager.setFragmentResultListener(
            PermissionDialogFragment.RESULT,
            activity,
        ) { _, result ->
            val route = result.getString("route") ?: return@setFragmentResultListener
            val kind =
                PermissionKind.entries.firstOrNull { it.name == result.getString("kind") }
                    ?: return@setFragmentResultListener
            when (result.getString("action")) {
                "continue",
                "settings" ->
                    request(route, kind, result.getString("action") == "settings" || kind.special)
                "skip" ->
                    handlers[route]?.result?.invoke(PermissionOutcome(kind, false, skipped = true))
                else ->
                    handlers[route]
                        ?.result
                        ?.invoke(PermissionOutcome(kind, false, cancelled = true))
            }
        }
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    releaseExternalUi()
                    away = false
                    model.resumed()
                    drain()
                }

                override fun onDestroy(owner: LifecycleOwner) = releaseExternalUi()

                override fun onStop(owner: LifecycleOwner) {
                    if (!activity.isChangingConfigurations) {
                        away = true
                        drain()
                    }
                }

                override fun onPause(owner: LifecycleOwner) {
                    if (!activity.isChangingConfigurations) {
                        away = true
                        model.leftHost()
                        drain()
                    }
                }
            }
        )
        // 必须允许等待系统授权时收到事件；普通业务扫描仍由各自的前台生命周期控制。
        activity.lifecycleScope.launch { model.state.collect { drain() } }
    }

    fun register(
        route: String,
        before: (PermissionKind) -> Unit,
        result: (PermissionOutcome) -> Unit,
    ) {
        register(route, before, onLaunch = {}, result = result)
    }

    /** 独立的实际启动钩子；原有 before 仍只表示请求入队，不能用于统计系统弹窗发起。 */
    fun register(
        route: String,
        before: (PermissionKind) -> Unit,
        onLaunch: (PermissionKind) -> Unit,
        result: (PermissionOutcome) -> Unit,
    ) {
        handlers[route] = Handler(before, result, onLaunch)
    }

    fun rationale(route: String, kind: PermissionKind?) {
        val manager = activity.supportFragmentManager
        if (
            manager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val old =
            manager.findFragmentByTag(PermissionDialogFragment.TAG) as? PermissionDialogFragment
        if (kind == null) {
            if (old?.route == route) old.dismiss()
            return
        }
        if (pending) return
        if (old?.route == route) return
        old?.dismissNow()
        val permissions = PermissionChecks.runtimePermissions(kind)
        val settings =
            !kind.special &&
                kind != PermissionKind.DIRECTORY &&
                model.requestedRuntime(kind) &&
                permissions.isNotEmpty() &&
                permissions.all { !activity.shouldShowRequestPermissionRationale(it) }
        PermissionDialogFragment.create(route, kind, settings)
            .showNow(manager, PermissionDialogFragment.TAG)
    }

    fun request(route: String, kind: PermissionKind, settings: Boolean = kind.special) {
        val handler = handlers[route] ?: return
        handler.before(kind)
        model.begin(route, kind, settings)
    }

    fun cancel() {
        model.cancel()
    }

    fun onReturnIntent(intent: Intent): Boolean {
        val id = intent.getStringExtra(PermissionSettingsNavigator.RETURN_REQUEST) ?: return false
        if (model.state.value?.id == id) {
            model.resumed()
            drain()
        }
        return true
    }

    private fun releaseExternalUi() {
        externalUi?.close()
        externalUi = null
    }

    private fun drain() {
        val value = model.state.value ?: return
        val resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        when (value.phase) {
            PermissionPhase.LAUNCH ->
                if (resumed) {
                    if (!model.launched(value.id)) return
                    if (model.state.value?.phase != PermissionPhase.WAITING) return
                    releaseExternalUi()
                    externalUi = ForegroundTransitionGuard.hold("permission:${value.kind}")
                    try {
                        handlers[value.route]?.launch?.invoke(value.kind)
                        when {
                            value.kind == PermissionKind.DIRECTORY -> directory.launch(null)
                            value.settings ->
                                if (
                                    !PermissionSettingsNavigator.open(
                                        activity,
                                        value.kind,
                                        access.notificationComponent,
                                    )
                                )
                                    model.launchFailed(value.id)
                            else -> runtime.launch(PermissionChecks.runtimePermissions(value.kind))
                        }
                    } catch (_: ActivityNotFoundException) {
                        releaseExternalUi()
                        model.launchFailed(value.id)
                    } catch (_: SecurityException) {
                        releaseExternalUi()
                        model.launchFailed(value.id)
                    }
                }
            PermissionPhase.RESULT ->
                if (resumed) {
                    val handler = handlers[value.route] ?: return
                    val result = model.consume() ?: return
                    releaseExternalUi()
                    if (result.unavailable)
                        Toast.makeText(activity, R.string.permission_unavailable, Toast.LENGTH_LONG)
                            .show()
                    handler.result(
                        PermissionOutcome(
                            result.kind,
                            result.granted,
                            result.directory,
                            result.unavailable,
                        )
                    )
                } else if (
                    away &&
                        !activity.isChangingConfigurations &&
                        !activity.isFinishing &&
                        value.leftHost &&
                        value.settings &&
                        value.granted &&
                        model.markReturnAttempted(value.id)
                ) {
                    // 系统可能拦截后台拉起，甚至不抛异常。仅尝试一次；手动返回会消费同一个结果。
                    PermissionSettingsNavigator.returnToApp(activity, value.id)
                }
            else -> Unit
        }
    }
}
