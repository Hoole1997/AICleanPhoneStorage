package com.example.aicleanphonestorage.core.locale

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** 不持有 Activity 集合。首帧闸门随每个 Activity 销毁移除，避免异步恢复产生旧语言闪屏。 */
internal class LanguageActivityCallbacks(private val languages: AppLanguageController) :
    Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity !is AppCompatActivity) return
        val decor = activity.window.decorView
        decor.layoutDirection = View.LAYOUT_DIRECTION_LTR
        if (languages.canDraw(activity)) return
        val observer = decor.viewTreeObserver
        val deadline = android.os.SystemClock.uptimeMillis() + 2000
        val gate =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (
                        !languages.canDraw(activity) &&
                            android.os.SystemClock.uptimeMillis() < deadline
                    )
                        return false
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    return true
                }
            }
        observer.addOnPreDrawListener(gate)
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (observer.isAlive) observer.removeOnPreDrawListener(gate)
                    owner.lifecycle.removeObserver(this)
                }
            }
        )
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
