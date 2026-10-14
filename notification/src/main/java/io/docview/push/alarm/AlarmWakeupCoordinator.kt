package io.docview.push.alarm

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.docview.push.host.PushEnvironment
import io.docview.push.host.PushUserChannel
import io.docview.push.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** 调度、业务发送和配置读取各自独立；所有状态操作串行，发通知时不占用状态锁。 */
internal class AlarmWakeupCoordinator(
    context: Context,
    private val readRemoteConfig: suspend () -> String?,
    private val awaitAudience: suspend () -> Unit,
    private val dispatch: suspend (intervalMillis: Long, canSend: () -> Boolean) -> Unit,
) : DefaultLifecycleObserver {
    private val store = AndroidAlarmWakeupStore(context)
    // 与元数据同在首次 IO 初始化中读取一次；首次离线或线上参数未下发也使用真实打包的默认策略。
    private val defaultConfigJson = context.assets.open("alarm_wakeup_config.json").bufferedReader().use { it.readText() }
    private val loop = AlarmWakeupLoop(store, AndroidAlarmWakeup(context), SystemClock::elapsedRealtime,
        { AndroidAlarmWakeup.bootCount(context) }, { UUID.randomUUID().toString() })
    private val mutex = Mutex()
    @Volatile private var policy = AlarmWakeupPolicy()

    /** 生命周期注册发生在主线程；磁盘读取/Alarm Binder 调用均由有限 IO scope 执行。 */
    fun start() { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        PushEnvironment.scope.launch {
            try {
                awaitAudience()
                mutex.withLock {
                    // 首次真实进入 App 后才允许开机/升级广播恢复，后台冷进程不伪装成首次使用。
                    store.hasLaunched = true
                    loop.reconcile(resolvePolicy())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.e("Alarm 前台恢复失败", error) }
        }
    }

    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        loop.reconcile(resolvePolicy(), force)
    }

    suspend fun deliver(token: String) {
        val accepted = mutex.withLock { loop.beginDelivery(token, resolvePolicy()) }
        if (!accepted) return
        try {
            // 一次投递只检查一次，不补执行错过的周期；沿用普通通知的全部准入规则。
            dispatch(policy.intervalMinutes * 60_000L) { policy.enabled }
        } finally {
            // Receiver 超时/发送失败也重新注册；配置更新已发布到 policy，清理阶段不再等待解密。
            withContext(NonCancellable) {
                mutex.withLock { loop.finishDelivery(policy) }
            }
        }
    }

    /** 一次闹钟已投递但初始化/检查中断时，不能把仍存在的 PI 误认为系统仍有待执行闹钟。 */
    suspend fun recoverDelivery(token: String) = mutex.withLock {
        if (store.registration?.token == token && loop.beginDelivery(token, resolvePolicy())) {
            loop.finishDelivery(policy)
        }
    }

    private suspend fun resolvePolicy(): AlarmWakeupPolicy {
        // 有效线上值优先；暂不可读、未下发或格式错误时使用有效缓存，其次使用本地默认 JSON。
        val remote = try { withTimeout(500L) { readRemoteConfig() } }
        catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive() // 外层 Receiver 被取消时仍协作式退出。
            null // 配置读取超时不能覆盖有效缓存或阻止本地默认规则启用。
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Logger.e("Alarm 配置读取失败", error); null }
        try {
            val cached = store.cachedConfig
            val resolved = resolveAlarmWakeupConfig(remote, cached, defaultConfigJson)
            val tier = resolved.config.policyFor(PushUserChannel.isConfirmedPaidUser())
            // 先发布关闭状态再提交磁盘；提交失败也不能让正在构建的通知沿用旧开关。
            policy = tier.copy(enabled = store.hasLaunched && tier.enabled)
            resolved.remoteJson?.takeIf { it != cached }?.let { store.cachedConfig = it }
            return policy
        } catch (error: Exception) {
            policy = AlarmWakeupPolicy()
            throw error
        }
    }
}
