package com.example.aicleanphonestorage.core.analytics

import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*

/** 仅持有页面标识和时间；旋转复用 ViewModel，不把页面重建误算为一次新访问。 */
internal class PageVisitState : ViewModel() {
    var page: String? = null
        private set
    private var since = 0L
    private val contentEvents = mutableSetOf<String>()
    fun enter(value: String, now: Long): Boolean {
        if (page != null) return false
        page = value; since = now
        contentEvents.clear()
        return true
    }
    fun once(event: String): Boolean = page != null && contentEvents.add(event)

    fun leave(now: Long, temporarilyCovered: Boolean = false): Map<String, Any>? {
        if (temporarilyCovered) return null
        val value = page ?: return null
        page = null
        return mapOf("page" to value, "stay_duration" to (now - since).coerceAtLeast(0) / 1000.0)
    }
}

internal object PageTelemetry {
    fun attach(activity: AppCompatActivity, page: String, permission: suspend () -> String = { "none" }) {
        attachDynamic(activity, { page }, permission)
    }

    /** 同一 Activity 的扫描/结果是不同逻辑页面；仅在前台真实切换时配对 leave/show。 */
    fun attachDynamic(
        activity: AppCompatActivity,
        page: () -> String,
        permission: suspend () -> String = { "none" },
    ): () -> Unit {
        val state = ViewModelProvider(activity)[PageVisitState::class.java]
        val refresh: () -> Unit = refresh@{
            if (activity.isFinishing || activity.isDestroyed ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@refresh
            val value = BusinessPageNames.wire(page())
            val now = SystemClock.elapsedRealtime()
            if (state.page != null && state.page != value)
                state.leave(now)?.let { BusinessTelemetry.emit(MetricEvent.PAGE_LEAVE, it) }
            if (state.enter(value, now)) {
                BusinessTelemetry.withPermission(MetricEvent.PAGE_SHOW, mapOf("page" to value), permission)
                val event = when (value) {
                    "traffic" -> MetricEvent.TRAFFIC_PAGE_SHOW
                    "notify" -> MetricEvent.NOTIFY_PAGE_SHOW
                    "BatteryInfo" -> MetricEvent.BATTERYINFO_RESULT_SHOW
                    else -> null
                }
                if (event == MetricEvent.BATTERYINFO_RESULT_SHOW) BusinessTelemetry.emit(event)
                else if (event != null) BusinessTelemetry.withPermission(event, emptyMap(), permission)
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = refresh()
            override fun onStop(owner: LifecycleOwner) {
                if (!activity.isChangingConfigurations)
                    state.leave(SystemClock.elapsedRealtime(), temporarilyCovered =
                        com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard.blocked && !activity.isFinishing)
                        ?.let { BusinessTelemetry.emit(MetricEvent.PAGE_LEAVE, it) }
            }
        })
        return refresh
    }
}

/** 进程首次前台为 cold，后续真实回前台为 hot；不依赖是否满足开屏广告限频。 */
internal class LaunchTelemetry : DefaultLifecycleObserver {
    private var launched = false
    init { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) {
        BusinessTelemetry.emit(MetricEvent.APP_LAUNCH, mapOf("launch_type" to if (launched) "hot" else "cold"))
        launched = true
    }
}

internal class HomeExposureState : ViewModel() { var last: Map<String, Any>? = null }
