package io.docview.push.utils

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.docview.push.utils.DateUtil
import io.docview.push.utils.Topic
import io.docview.push.utils.Logger

/**
 * FCM 主题订阅工具类
 */
object TopicMgr {
    private const val TAG = "TopicMgr"

    /**
     * 订阅通用主题
     */
    fun subscribeCommonTopic() {
        // 全量推送（推送给所有用户）
        subscribeToTopic(Topic.ALL_TOKEN)
        // 全量推送（推送给所有用户），指定时区定时
        subscribeToTopic(DateUtil.getFirebaseTopicWithTimezone(Topic.ALL_TOKEN))
    }

    /**
     * 订阅指定主题
     * @param topic 主题名称
     */
    fun subscribeToTopic(topic: String) {
        Logger.d("订阅主题: $topic")
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                var msg = "订阅成功"
                if (!task.isSuccessful) {
                    msg = "订阅失败"
                }
                Logger.d("主题订阅结果:[$topic] $msg")
            }
    }

    /**
     * 取消订阅指定主题
     * @param topic 主题名称
     */
    fun unsubscribeFromTopic(topic: String) {
        Logger.d("取消订阅主题: $topic")
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                var msg = "取消订阅成功"
                if (!task.isSuccessful) {
                    msg = "取消订阅失败"
                }
                Logger.d("主题取消订阅结果:[$topic] $msg")
            }
    }

    /**
     * 记录订阅状态
     */
    fun logSubscriptionStatus() {
        Logger.d("=== FCM 主题订阅状态 ===")
        Logger.d("已订阅主题:")
        Logger.d("- ${Topic.ALL_TOKEN} (全量推送)")
        Logger.d("- ${DateUtil.getFirebaseTopicWithTimezone(Topic.ALL_TOKEN)} (时区推送)")
        Logger.d("========================")
    }

    /**
     * 获取 FCM 令牌
     */
    fun getFCMToken(callback: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Logger.d("FCM token acquired")
                    callback(token)
                } else {
                    Logger.e("获取 FCM Token 失败", task.exception)
                    callback(null)
                }
            }
    }
}
