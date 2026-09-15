package io.docview.push.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

/** 独立于通知触发策略的埋点快照；不使用带后台延迟的 ProcessLifecycleOwner 来推断点击前状态。 */
object NotificationVisibility : Application.ActivityLifecycleCallbacks {
    private val installed = AtomicBoolean()
    private val state = NotificationVisibilityState()
    fun install(application: Application) {
        if (installed.compareAndSet(false, true)) application.registerActivityLifecycleCallbacks(this)
    }
    fun backgroundForDisplay() = state.backgroundForDisplay()
    fun backgroundForClick() = state.backgroundForClick()
    override fun onActivityStarted(activity: Activity) = state.started()
    override fun onActivityResumed(activity: Activity) = state.resumed()
    override fun onActivityStopped(activity: Activity) = state.stopped()
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
