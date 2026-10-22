package io.docview.push.boot

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.docview.push.host.PushEnvironment
import io.docview.push.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** 单独记录用户打开行为；与埋点前后台快照、推送触发控制器解耦，不持有 Activity。 */
internal object BootRestoreLifecycle : Application.ActivityLifecycleCallbacks {
    private val installed = AtomicBoolean()
    private val recorded = AtomicBoolean()
    fun install(application: Application) {
        if (installed.compareAndSet(false, true)) application.registerActivityLifecycleCallbacks(this)
    }
    override fun onActivityStarted(activity: Activity) {
        BootRestoreSession.allowNormalStart(background = false)
        if (!recorded.compareAndSet(false, true)) return
        val app = activity.applicationContext
        PushEnvironment.scope.launch {
            try { BootRestoreStore(app).recordOpened() }
            catch (cancelled: CancellationException) { recorded.set(false); throw cancelled }
            catch (error: Exception) {
                recorded.set(false)
                Logger.e("开机恢复：保存已打开应用状态失败", error)
            }
        }
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
