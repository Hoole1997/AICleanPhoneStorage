package io.docview.push.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.blankj.utilcode.util.ServiceUtils
import io.docview.push.controller.TriggerCtrl
import io.docview.push.utils.Logger
import io.docview.push.host.canSendNotification
import io.docview.push.host.PushEventReporter

/**
 * 前台保活服务管理器
 * 提供便捷的服务控制接口
 */
object KeepAliveServiceManager {

    private const val TAG = "KeepAliveServiceManager"

    /**
     * 启动保活服务
     * @param context 上下文
     * @param intervalSeconds 间隔时间（秒），默认使用持久化存储的值
     */
    fun startKeepAliveService(context: Context,from: String = "localPush" ) {
        if (!io.docview.push.host.PushEnvironment.host.backgroundServiceEnabled) {
            TriggerCtrl.ensureResidentNotificationExists()
            return
        }
        try {
            PushEventReporter.reportData("Notific_Pull", mapOf("topic" to "permanent"))
            if(!context.canSendNotification()){
                PushEventReporter.reportData("Notific_Show_Fail",mapOf("reason" to "alive_service_${from}_no_permission"))
                Logger.d("无通知权限，前台服务忽略启动")
                return
            }
            if (isKeepAliveServiceRunning()) {
                // 服务已运行，更新通知栏
                Logger.d("保活服务已在运行中，刷新通知栏")
                CoreService.updateNotification(context)
            } else {
                // 服务未运行，启动服务
                try {
                    CoreService.startService(context)
                    Logger.d("保活服务启动请求已发送")
                }
                catch (e: Exception){
                    Logger.e("启动保活服务失败,尝试使用contentResolver方式启动服务", e)
                }
                startWithCall(context,from)
            }
        } catch (e: Exception) {
            TriggerCtrl.ensureResidentNotificationExists()
            PushEventReporter.reportData("Notific_Show_Fail",mapOf("reason" to "alive_service_${from}_${e.message}"))
            Logger.e("启动保活服务失败", e)
        }
    }

    private fun startWithCall(context: Context,from: String ) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!isKeepAliveServiceRunning()) {
                    val contentResolver = context.contentResolver
                    contentResolver.call(
                        "content://${context.packageName}.notification.provider".toUri(),
                        Intent(
                            context,
                            CoreService::class.java
                        ).component?.className ?: "",
                        "",
                        null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                PushEventReporter.reportData("Notific_Show_Fail",mapOf("reason" to "alive_service_${from}_${e.message}"))
            }
        }, 1000)
    }

    /**
     * 检查保活服务是否在运行
     * @return 是否在运行
     */
    fun isKeepAliveServiceRunning(): Boolean {
        return ServiceUtils.isServiceRunning(CoreService::class.java)
    }
}
