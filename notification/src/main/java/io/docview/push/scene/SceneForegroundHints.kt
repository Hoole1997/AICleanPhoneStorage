package io.docview.push.scene

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.docview.push.host.PushEnvironment
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** 前台只给可见业务页面发短提示；不保留 Activity/View，不缓存提示到下次回前台，不发系统通知。 */
internal object SceneForegroundHints : Application.ActivityLifecycleCallbacks {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val last = mutableMapOf<PushScene, Long>()
    private var installed = false
    fun install(app: Application) { if (!installed) { installed = true; app.registerActivityLifecycleCallbacks(this) } }
    fun offer(event: SceneEvent, copy: SceneSelection) {
        if (!io.docview.push.NotificationPermissionAccess.isGranted(PushEnvironment.context)) return
        val now = SystemClock.elapsedRealtime()
        if (last[event.scene]?.let { now - it < 60_000L } == true) return
        last[event.scene] = now
        events.tryEmit(SceneRichText.plain(copy.text.text).take(500))
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is AppCompatActivity || !PushEnvironment.host.supportsSceneForegroundHint(activity.javaClass.name)) return
        // 观察属于页面 lifecycleScope；Application 回调没有捕获或保存 Activity 集合。
        activity.lifecycleScope.launch { activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            events.collect { text ->
                if (!activity.isFinishing && activity.hasWindowFocus()) activity.findViewById<View>(android.R.id.content)?.let {
                    Snackbar.make(it, text, Snackbar.LENGTH_SHORT).show()
                }
            }
        } }
    }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
