package io.docview.push.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.docview.push.controller.TriggerCtrl
import io.docview.push.utils.Logger

/**
 * 通知删除监听器
 * 用于监听通知的删除事件（滑动删除）
 */
class DeleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "io.docview.push.ACTION_NOTIFICATION_DELETE" -> {
                Logger.d("通知被删除（滑动删除），停止重复通知")

                // 停止重复通知
                TriggerCtrl.stopRepeatNotification()
            }
        }
    }
}
