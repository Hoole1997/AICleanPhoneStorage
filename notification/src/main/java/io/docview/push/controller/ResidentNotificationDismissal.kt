package io.docview.push.controller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.docview.push.NotificationPermissionAccess
import io.docview.push.receiver.ResidentDismissReceiver
import io.docview.push.service.CoreService
import io.docview.push.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** 常驻通知的原生划除契约；不复用普通通知的删除 action，也不通过删除回调重启服务。 */
internal object ResidentNotificationDismissal {
    const val ACTION = "io.docview.push.ACTION_RESIDENT_NOTIFICATION_DELETE"
    private val restoring = Mutex()

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        TriggerCtrl.getResidentNotificationId(),
        Intent(context.applicationContext, ResidentDismissReceiver::class.java)
            .setAction(ACTION)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun restoreIfServiceRunning(context: Context) {
        // 重叠的回调只恢复一次；下一次真实划除仍可再次恢复，不使用轮询或固定延时重试。
        if (!restoring.tryLock()) return
        val app = context.applicationContext
        try {
            if (!CoreService.isRunning || !NotificationPermissionAccess.isGranted(app, TriggerCtrl.CHANNEL_ID_RESIDENT)) {
                Logger.d("[resident_delete] skip: service stopped or notification permission disabled")
                return
            }
            val notification = withContext(Dispatchers.IO) { TriggerCtrl.buildResidentNotification(app) }
            withContext(Dispatchers.Main.immediate) {
                // 构建期间服务可能被停止/权限可能被撤销，发布前必须再次检查。
                if (!CoreService.isRunning || !NotificationPermissionAccess.isGranted(app, TriggerCtrl.CHANNEL_ID_RESIDENT)) {
                    Logger.d("[resident_delete] skip: state changed before posting")
                    return@withContext
                }
                // 同一 ID 更新现有 FGS 通知；不调用 startService，也不触发 Runtime 的服务启动分支。
                try {
                    NotificationManagerCompat.from(app).notify(TriggerCtrl.getResidentNotificationId(), notification)
                } catch (_: SecurityException) {
                    // 最后一次权限检查和系统实际发布之间仍可能发生权限撤销。
                    Logger.w("[resident_delete] permission revoked while posting")
                    return@withContext
                }
                TriggerCtrl.residentShown(notification)
                Logger.d("[resident_delete] restored: id=${TriggerCtrl.getResidentNotificationId()}")
            }
        } finally {
            restoring.unlock()
        }
    }
}
