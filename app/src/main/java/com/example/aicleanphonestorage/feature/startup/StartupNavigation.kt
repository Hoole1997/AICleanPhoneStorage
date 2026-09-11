package com.example.aicleanphonestorage.feature.startup

import android.app.Activity
import android.app.ActivityOptions
import android.os.Build
import com.example.aicleanphonestorage.R
import android.content.Context
import android.content.Intent
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import io.docview.push.NotificationDestination

/** 启动页只转交白名单目标与一个调试标记，绝不复制通知正文、任意 URI 或完整 extras。 */
internal object StartupNavigation {
    const val COMPLETED = "startup.completed"
    const val PERMISSION_COMPLETED = "startup.permission.completed"

    fun read(intent: Intent) = StartupEntry(
        NotificationNavigation.read(intent) ?: NotificationDestination.HOME,
        HomePreviewSupport.initialSelection(intent, null),
    )

    fun needsStartup(intent: Intent) = !intent.getBooleanExtra(COMPLETED, false) && NotificationNavigation.read(intent) != null

    fun startupIntent(context: Context, entry: StartupEntry) =
        Intent(context, StartupActivity::class.java)
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, entry.destination.key)
            .putExtra(HomePreviewSupport.EXTRA_MODE, entry.previewMode)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun homeIntent(context: Context, entry: StartupEntry) =
        Intent(context, MainActivity::class.java)
            .putExtra(NotificationNavigation.EXTRA_DESTINATION, entry.destination.key)
            .putExtra(HomePreviewSupport.EXTRA_MODE, entry.previewMode)
            .putExtra(COMPLETED, true)
            .putExtra(PERMISSION_COMPLETED, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 仅定制启动页到首页；低版本兼容和窗口动画统一放在导航边界，不改变其他业务页面。 */
    @Suppress("DEPRECATION")
    fun openHome(activity: Activity, entry: StartupEntry, animate: Boolean) {
        val enter = if (animate) R.anim.startup_home_enter else 0
        val exit = if (animate) R.anim.startup_home_exit else 0
        val intent = homeIntent(activity, entry).apply {
            if (!animate) addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        if (Build.VERSION.SDK_INT >= 34) activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enter, exit)
        activity.startActivity(intent, ActivityOptions.makeCustomAnimation(activity, enter, exit).toBundle())
        activity.finish()
        if (Build.VERSION.SDK_INT < 34) activity.overridePendingTransition(enter, exit)
    }

}
