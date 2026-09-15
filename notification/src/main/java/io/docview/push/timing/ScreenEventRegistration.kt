package io.docview.push.timing

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import io.docview.push.utils.Logger

/**
 * 单个 owner 的注册句柄。Application 只持有 Application 句柄，Service 自己持有 Service 句柄，
 * 全局分发器不保存 Service/Activity Context。注册成功后才发布句柄，失败可由另一个 owner 独立重试。
 */
internal class ScreenEventRegistration private constructor(
    private val context: Context,
    private val receiver: BroadcastReceiver,
    private val owner: ScreenListenerOwner,
) : AutoCloseable {
    private var closed = false

    @MainThread
    override fun close() {
        if (closed) return
        closed = true
        try {
            context.unregisterReceiver(receiver)
            Logger.d("屏幕监听已注销: owner=$owner")
        } catch (error: IllegalArgumentException) {
            // Service Context 被框架清理后再次销毁，仍允许其他 owner 正常工作。
            Logger.w("屏幕监听已由系统注销: owner=$owner")
        }
    }

    companion object {
        @MainThread
        fun register(
            context: Context,
            owner: ScreenListenerOwner,
            onEvent: (ScreenListenerOwner, ScreenEvent, Boolean, Boolean) -> Unit,
        ): ScreenEventRegistration {
            val power = requireNotNull(context.getSystemService(PowerManager::class.java))
            val keyguard = requireNotNull(context.getSystemService(KeyguardManager::class.java))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val event = when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> ScreenEvent.OFF
                        Intent.ACTION_SCREEN_ON -> ScreenEvent.ON
                        Intent.ACTION_USER_PRESENT -> ScreenEvent.UNLOCK
                        else -> return
                    }
                    onEvent(owner, event, power.isInteractive, keyguard.isKeyguardLocked)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            // OEM SystemUI 可使用独立 UID。这里只订阅系统受保护广播，不能混入自定义 action。
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            Logger.d("屏幕监听已注册: owner=$owner")
            return ScreenEventRegistration(context, receiver, owner)
        }
    }
}
