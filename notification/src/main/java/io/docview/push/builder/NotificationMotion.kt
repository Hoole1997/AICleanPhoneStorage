package io.docview.push.builder

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.widget.RemoteViews
import io.docview.push.R

/** 只负责通知视图动效策略；不接触发布、循环、文案轮播、计数或点击路由。 */
internal object NotificationMotion {
    fun allowed(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return power.isInteractive && !power.isPowerSaveMode &&
            (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())
    }

    fun applyLegacy(views: RemoteViews, animate: Boolean) {
        // 两层均显式设置，保证同 ID reapply 从动态切换到静态时真正停止 ProgressBar。
        views.setViewVisibility(R.id.notificationButtonMotion, if (animate) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.notificationButtonStatic, if (animate) View.GONE else View.VISIBLE)
    }
}
