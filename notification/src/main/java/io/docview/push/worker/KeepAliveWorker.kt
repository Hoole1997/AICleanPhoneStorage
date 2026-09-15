package io.docview.push.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.docview.push.check.CheckCtrl
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.service.KeepAliveServiceManager
import io.docview.push.timing.TimingCtrl
import io.docview.push.utils.Logger
import kotlinx.coroutines.delay

/**
 * 通知保活 Worker
 * 用于定期检查并确保常驻通知存在
 */
class KeepAliveWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "notification_keep_alive"

        /**
         * 创建保活工作请求
         * @param context 上下文
         * @return 工作请求
         */
        fun createWorkRequest(context: Context) {
            // 这里可以添加具体的 WorkManager 配置
            // 例如：周期性工作、约束条件等
            Logger.d("创建通知保活工作请求")
        }
    }

    override suspend fun doWork(): Result {
        // 历史任务可能在迁移取消前已被调度；事件监听模式不执行任何周期工作。
        if (!io.docview.push.host.PushEnvironment.host.periodicPushEnabled) return Result.success()
        return try {
            Logger.d("开始执行通知保活任务")

            EarthquakeController.checkAndTriggerScheduledPush()

            // 检查并确保常驻通知存在
            KeepAliveServiceManager.startKeepAliveService(context = context,from = "workManager")

            // 和保活服务15分钟重复了
            // 尝试触发通知
//            TimingCtrl.getInstance().triggerNotificationIfAllowed(CheckCtrl.NotificationType.KEEPALIVE)


            // 等待一段时间确保通知发送完成
            delay(1000)

            Logger.d("通知保活任务执行完成")
            Result.success()

        } catch (e: Exception) {
            Logger.e("通知保活任务执行失败", e)
            Result.failure()
        }
    }
}
