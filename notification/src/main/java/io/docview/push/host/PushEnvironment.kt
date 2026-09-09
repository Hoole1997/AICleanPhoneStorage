package io.docview.push.host

import android.content.Context
import io.docview.push.NotificationHost
import io.docview.push.NotificationPermissionAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 新模块与宿主之间唯一应用级边界，只持有 Application 和无 View 的业务接口。 */
internal object PushEnvironment {
    lateinit var context: Context
        private set
    lateinit var host: NotificationHost
        private set
    // 来源控制器共用受限 IO 调度器，不为每个事件创建线程/无归属 Scope。
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    fun install(context: Context, host: NotificationHost) {
        this.context = context.applicationContext
        this.host = host
    }
}

internal fun Context.canSendNotification() = NotificationPermissionAccess.isGranted(this)

/** 事件交给宿主注入的统计出口；不把 token/签名/用户标识记录到日志或默认第三方服务。 */
internal object PushEventReporter {
    fun reportData(name: String, properties: Map<String, Any?> = emptyMap()) {
        PushEnvironment.host.onEvent(name, properties.filterKeys { it !in setOf("token", "sig", "userid") })
    }
}
