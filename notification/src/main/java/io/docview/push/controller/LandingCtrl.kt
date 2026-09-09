package io.docview.push.controller

import android.content.Context
import android.content.Intent
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.builder.LANDING_NOTIFICATION_FROM
import io.docview.push.builder.LANDING_NOTIFICATION_ID
import io.docview.push.builder.NotificationType
import io.docview.push.builder.markRedPointClicked
import io.docview.push.builder.type2notificationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 通知落地页控制器
 * 处理通知跳转和应用启动逻辑
 */
object LandingCtrl {

    private const val TAG = "LandingCtrl"

    // 协程作用域，用于异步处理
    private val landingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 检查是否来自通知跳转
     * @param intent Intent 对象
     * @return true 如果来自通知跳转，false 如果不是
     */
    fun isFromNotification(intent: Intent): Boolean {
        return intent.action == "io.docview.push.ACTION_OPEN_APP" ||
                intent.getBooleanExtra("from_notification", false)
    }


    /**
     * 获取通知动作类型
     * @param intent Intent 对象
     * @return 动作类型，如果没有则返回默认值
     */
    fun getNotificationActionType(intent: Intent): Int {
        return intent.getIntExtra(LANDING_NOTIFICATION_ACTION, io.docview.push.config.Content.TYPE_HOME)
    }

    /**
     * 获取通知时间戳
     * @param intent Intent 对象
     * @return 时间戳，如果没有则返回 0
     */
    fun getNotificationTimestamp(intent: Intent): Long {
        return intent.getLongExtra("notification_timestamp", 0L)
    }

    /**
     * 检查是否需要标记红点已点击
     * @param intent Intent 对象
     * @return true 如果需要标记，false 如果不需要
     */
    fun shouldMarkRedPointClicked(intent: Intent): Boolean {
        return intent.getBooleanExtra("mark_red_point_clicked", false)
    }

    fun handleResidentRedPoint(context: Context, intent: Intent) {
        if (intent.getIntExtra(
                LANDING_NOTIFICATION_ID,
                -1
            ) == type2notificationId[NotificationType.PROCESS]
        ) {
            markRedPointClicked(context)
        }
    }

    /**
     * 清理Intent中的通知相关参数
     * @param intent Intent 对象
     */
    fun clearNotificationParameters(intent: Intent) {
        // 清理通知相关参数
        intent.removeExtra("from_notification")
        intent.removeExtra(LANDING_NOTIFICATION_ACTION)
        intent.removeExtra("notification_timestamp")
        intent.removeExtra("mark_red_point_clicked")
        intent.removeExtra(LANDING_NOTIFICATION_ID)
        intent.removeExtra(LANDING_NOTIFICATION_FROM)

        // 清理通知动作
        intent.action = null
    }

}
