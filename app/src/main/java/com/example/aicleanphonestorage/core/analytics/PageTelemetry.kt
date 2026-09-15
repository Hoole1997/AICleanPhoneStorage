package com.example.aicleanphonestorage.core.analytics

import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*

/** 仅持有页面标识和时间；旋转复用 ViewModel，不把页面重建误算为一次新访问。 */
internal class PageVisitState : ViewModel() {
    private var page: String? = null
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
        val state = ViewModelProvider(activity)[PageVisitState::class.java]
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // ActivityResult 可能在 STARTED 时直接转发到首页，不能把短暂露出的父页计成新访问。
                if (activity.isFinishing || activity.isDestroyed) return
                if (state.enter(page, SystemClock.elapsedRealtime())) {
                    BusinessTelemetry.withPermission(MetricEvent.PAGE_SHOW, mapOf("page" to page), permission)
                    val event = when (page) {
                        "traffic" -> MetricEvent.TRAFFIC_PAGE_SHOW
                        "notify" -> MetricEvent.NOTIFY_PAGE_SHOW
                        else -> null
                    }
                    if (event != null) BusinessTelemetry.withPermission(event, emptyMap(), permission)
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                if (!activity.isChangingConfigurations)
                    state.leave(SystemClock.elapsedRealtime(), temporarilyCovered =
                        com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard.blocked && !activity.isFinishing)
                        ?.let { BusinessTelemetry.emit(MetricEvent.PAGE_LEAVE, it) }
            }
        })
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
