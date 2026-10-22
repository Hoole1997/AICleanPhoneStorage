package com.example.aicleanphonestorage.feature.retention

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.aicleanphonestorage.core.locale.AppLanguageController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.corekit.core.controller.ChannelUserController

/** 应用级事件协调器只持有 Application。并发事件合并为一次短任务，没有 Service/定时器/后台轮询。 */
internal class RetentionRuntime(context: Context, private val readPolicy: suspend () -> Boolean = { RetentionPolicy.read() }) {
    private val app = context.applicationContext
    private val publisher = RetentionShortcut(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val lock = Mutex()
    private val current = MutableStateFlow<Boolean?>(null)
    val allowed = current.asStateFlow()
    private var started = false

    fun start(languages: AppLanguageController) {
        if (started) return
        started = true
        ChannelUserController.addChannelChangeListener(object : ChannelUserController.ChannelChangeListener {
            override fun onChannelChanged(oldChannel: ChannelUserController.UserChannelType, newChannel: ChannelUserController.UserChannelType) = refresh()
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = refresh()
        })
        scope.launch {
            languages.ready.await()
            for (ignored in updates) try {
                lock.withLock {
                    val enabled = readPolicy()
                    current.value = enabled
                    val tag = languages.selected.value
                    val localized = if (tag.isEmpty()) app else app.createConfigurationContext(
                        Configuration(app.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) })
                    publisher.sync(enabled, localized)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("RetentionShortcut", "Shortcut update unavailable", error) }
        }
        scope.launch { languages.selected.collect { refresh() } }
        refresh()
    }

    fun refresh() { updates.trySend(Unit) }

    fun recordEntry() { scope.launch {
        try { publisher.recordUse() }
        catch (error: RuntimeException) { android.util.Log.w("RetentionShortcut", "Shortcut usage unavailable", error) }
    } }

    /** 启动和页面恢复都即时复核，防止旧快捷方式绕过已关闭的参数；不等待广告初始化。 */
    suspend fun checkNow(): Boolean = lock.withLock { readPolicy().also { current.value = it } }
}
