package io.docview.push.scene

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.docview.push.analytics.NotificationVisibility
import io.docview.push.host.PushEnvironment
import io.docview.push.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** CoreService 的唯一场景监听会话；停止、超时、销毁时一起取消任务并注销所有系统监听。 */
internal class SceneEventRegistration private constructor(
    context: Context, parent: CoroutineScope, private val repository: SceneRepository, boot: Int,
) : AutoCloseable {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) +
        CoroutineExceptionHandler { _, error -> Logger.w("场景推送：监听任务异常结束，异常类型=${error.javaClass.simpleName}") })
    private val clock = { SceneMoment(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot) }
    private val refreshLock = Mutex()
    private val presentation = ScenePresentationQueue(Logger::d)
    private val repeats = SceneRepeatCoordinator(context, scope, { repository.snapshot.enabled(it) })
    private val publisher = ScenePublisher(context, repeats)
    private lateinit var dispatcher: SceneDispatcher
    private val devices = DeviceSceneSources(context, scope, clock) { signal ->
        // 状态边沿只撤销尚未展示的候选。首条成功后展示周期独立，不被后续广播/快照更新打断。
        signal.cancel.forEach { dispatcher.cancel(it) }
        signal.events.forEach { event ->
            Logger.d("场景推送：${event.scene.logName} 状态变化满足触发规则，产生待处理事件")
            if (event.scene == PushScene.CHARGE_START && !NotificationVisibility.backgroundForDisplay()) {
                Logger.d("场景推送：${event.scene.logName} 插电时应用在前台，仅处理页面提示")
                // 充电开始额外要求“插电当时已在后台”：前台插电只提示页面，不能等以后退后台再发。
                dispatcher.cancel(event.scene)
                if (repository.snapshot.enabled(event.scene)) SceneCopies.select(repository.snapshot.copies[event.scene].orEmpty(),
                    "", event, PushEnvironment.host.contentLanguageTag)?.let { SceneForegroundHints.offer(event, it) }
            } else dispatcher.offer(event)
        }
    }
    private val files = FileSceneSources(context, scope, repository.storage, { repository.snapshot }, clock) {
        Logger.d("场景推送：${it.scene.logName} 文件或应用事件满足触发规则，产生待处理事件")
        dispatcher.offer(it)
    }
    private var closed = false
    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            Logger.d("场景推送：应用回到前台，清空触发队列并结束新场景循环")
            presentation.clear()
            repeats.cancelAll() // 点击进入 App/主动回前台后，不继续延长悬浮；不触及旧循环。
            scope.launch {
                repository.refresh()
                refreshSources() // 权限可能变化而配置值未变，仍需重新判断 MediaStore 观察资格。
            }
        }
    }

    init {
        dispatcher = SceneDispatcher(scope, { repository.snapshot }, repository.storage, clock,
            { PushEnvironment.host.contentLanguageTag }, NotificationVisibility::backgroundForDisplay,
            validate = ::validate, publish = publisher::publish, foregroundHint = SceneForegroundHints::offer,
            refreshFacts = devices::fresh,
            presentation = presentation,
            log = Logger::d,
            failed = { scene, error -> Logger.w("场景推送：${scene.logName} 调度异常，本次已跳过，异常类型=${error.javaClass.simpleName}") })
        Logger.d("场景推送：服务监听会话已创建，启用串行触发队列")
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        scope.launch { repository.state.collect { if (it != null) refreshSources() } }
    }

    private suspend fun refreshSources() = refreshLock.withLock {
        if (closed) return@withLock
        repeats.reconcile()
        dispatcher.reconsider()
        devices.refresh(repository.snapshot)
        files.refresh(repository.snapshot)
    }

    override fun close() {
        if (closed) return
        Logger.d("场景推送：服务监听会话关闭，释放队列、循环和系统监听")
        closed = true
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        presentation.close(); dispatcher.close(); repeats.close(); devices.close(); files.close(); scope.cancel()
    }

    private suspend fun validate(event: SceneEvent): Boolean = when (event.scene) {
        PushScene.CHARGE_START, PushScene.CHARGE_END, PushScene.LOW_BATTERY, PushScene.NETWORK -> devices.valid(event)
        else -> files.valid(event)
    }

    companion object {
        suspend fun create(context: Context, parent: CoroutineScope, repository: SceneRepository): AutoCloseable {
            repository.initialize()
            val boot = withContext(Dispatchers.IO) { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
            return SceneEventRegistration(context, parent, repository, boot)
        }
    }
}
