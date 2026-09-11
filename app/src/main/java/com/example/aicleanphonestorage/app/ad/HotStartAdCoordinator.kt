package com.example.aicleanphonestorage.app.ad

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.feature.startup.StartupActivity
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** 进程 ON_STOP/ON_START 判定热启动；AndroidX 会过滤旋转与普通 Activity 切换的短暂间隙。 */
internal class HotStartAdCoordinator(
    private val application: Application,
    private val process: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val eligible: suspend () -> Boolean = { HotStartAdEligibility.allowed() },
    private val blocked: () -> Boolean = { ForegroundTransitionGuard.blocked },
    private val open: (Activity) -> Unit = { it.startActivity(StartupNavigation.hotIntent(it)) },
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver, AutoCloseable {
    private val state = HotStartState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var check: Job? = null
    private var resumed = WeakReference<Activity>(null)
    private var eligibleDeparture = false
    private var cycle = 0L

    init {
        application.registerActivityLifecycleCallbacks(this)
        process.addObserver(this)
        HotStartAdLog.event("listener_registered processState=${process.currentState}")
    }

    override fun onStart(owner: LifecycleOwner) {
        cycle++
        state.foreground()
        HotStartAdLog.event("process_foreground cycle=$cycle ${state.description()}")
    }

    override fun onStop(owner: LifecycleOwner) {
        check?.cancel()
        state.background(eligibleDeparture)
        HotStartAdLog.event("process_background cycle=$cycle eligibleDeparture=$eligibleDeparture ${state.description()}")
        eligibleDeparture = false
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        val hot = state.take()
        val business = isBusinessActivity(activity)
        val guarded = blocked()
        val name = activity.javaClass.simpleName
        HotStartAdLog.event("activity_resumed cycle=$cycle activity=$name hotCandidate=$hot business=$business guard=$guarded externalFlow=${ForegroundTransitionGuard.description()}")
        val rejection = when {
            !hot -> "no_hot_start_candidate"
            !business -> "startup_or_sdk_activity"
            guarded -> "external_ui_in_progress"
            else -> null
        }
        if (rejection != null) {
            HotStartAdLog.event("entry_skipped cycle=$cycle activity=$name reason=$rejection")
            return
        }
        val candidate = WeakReference(activity)
        val attempt = cycle
        check?.cancel()
        check = scope.launch {
            try {
                // API 28 及以前回调可能在 super.onResume 内分发，先让宿主完成生命周期恢复。
                yield()
                HotStartAdLog.event("entry_check_begin cycle=$attempt activity=$name")
                if (!eligible()) {
                    HotStartAdLog.event("entry_skipped cycle=$attempt reason=eligibility_rejected see=frequency_decision")
                    return@launch
                }
                val target = candidate.get()
                val lifecycle = (target as? LifecycleOwner)?.lifecycle?.currentState
                val reason = when {
                    target == null -> "activity_released"
                    resumed.get() !== target -> "activity_changed"
                    target.isFinishing || target.isDestroyed -> "activity_finishing_or_destroyed"
                    blocked() -> "external_ui_in_progress"
                    lifecycle?.isAtLeast(Lifecycle.State.RESUMED) != true -> "activity_not_resumed"
                    else -> null
                }
                if (reason != null) {
                    HotStartAdLog.event("entry_skipped cycle=$attempt activity=$name reason=$reason lifecycle=$lifecycle externalFlow=${ForegroundTransitionGuard.description()}")
                    return@launch
                }
                // 无 NEW_TASK/CLEAR_TOP：结束后系统恢复原页面及状态。
                HotStartAdLog.event("startup_open_requested cycle=$attempt activity=$name slot=splash")
                open(requireNotNull(target))
                HotStartAdLog.event("startup_open_dispatched cycle=$attempt activity=$name")
            } catch (cancelled: CancellationException) {
                HotStartAdLog.event("entry_check_cancelled cycle=$attempt activity=$name reason=lifecycle_changed_or_coordinator_closed")
                throw cancelled
            } catch (error: RuntimeException) {
                HotStartAdLog.failure("entry_failed cycle=$attempt activity=$name", error)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) {
            // 在离开时快照，防止授权/广告回调先结束 guard，随后返回被误认为新的热启动。
            eligibleDeparture = isBusinessActivity(activity) && !blocked()
            HotStartAdLog.event("activity_paused cycle=$cycle activity=${activity.javaClass.simpleName} eligibleDeparture=$eligibleDeparture externalFlow=${ForegroundTransitionGuard.description()} changingConfigurations=${activity.isChangingConfigurations}")
            resumed.clear()
            check?.cancel()
        }
    }

    private fun isBusinessActivity(activity: Activity) =
        activity !is StartupActivity &&
            activity.javaClass.name.startsWith("com.example.aicleanphonestorage.")

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (resumed.get() === activity) resumed.clear()
    }

    override fun close() {
        application.unregisterActivityLifecycleCallbacks(this)
        process.removeObserver(this)
        resumed.clear()
        scope.cancel()
    }
}
