package com.example.aicleanphonestorage.core.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 应用级仅保留 Context 和轻量状态。API 33+ 以系统应用语言为准，旧系统交给 AppCompat 应用配置。 */
internal class AppLanguageController(context: Context) {
    private val app = context.applicationContext
    private val preferences = LanguagePreferences(app)
    private val current = MutableStateFlow("")
    val selected = current.asStateFlow()
    val ready = CompletableDeferred<Unit>()
    private val writes = Mutex()
    private var started = false

    fun initialize() {
        if (started) return
        started = true
        // 只执行一次恢复/升级迁移；结束后销毁 Scope，不轮询、不启动 Service。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope
            .launch {
                try {
                    val stored = withTimeout(1500) { preferences.read() }
                    if (Build.VERSION.SDK_INT >= 33) {
                        val manager = app.getSystemService(LocaleManager::class.java)
                        if (!stored.migratedToFramework) {
                            if (
                                manager.applicationLocales.isEmpty && AppLanguages.valid(stored.tag)
                            )
                                manager.applicationLocales = LocaleList.forLanguageTags(stored.tag)
                            preferences.write(
                                manager.applicationLocales.toLanguageTags(),
                                migrated = true,
                            )
                        }
                    } else {
                        val tag = stored.tag.takeIf(AppLanguages::valid).orEmpty()
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tag)
                        )
                    }
                    refresh()
                } catch (_: TimeoutCancellationException) {
                    refresh()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    refresh()
                } // 偏好损坏/不可读时保留系统默认，不阻塞首帧。
                finally {
                    ready.complete(Unit)
                }
            }
            .invokeOnCompletion { scope.cancel() }
    }

    fun refresh() {
        current.value =
            if (Build.VERSION.SDK_INT >= 33)
                app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            else AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }

    suspend fun select(tag: String) {
        require(AppLanguages.valid(tag))
        ready.await()
        writes.withLock {
            // 确认选择后，原子保存与配置应用一起完成；即便页面因 Locale 变化重建也不丢失选择。
            withContext(NonCancellable) {
                preferences.write(tag, migrated = Build.VERSION.SDK_INT >= 33)
                withContext(Dispatchers.Main.immediate) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    refresh()
                }
            }
        }
    }

    /** 首次旧系统恢复异步完成前不显示错误语言；新 Activity 会由 AppCompat 取得已恢复配置。 */
    fun canDraw(context: Context): Boolean {
        if (!ready.isCompleted) return false
        val tag = current.value.substringBefore(',')
        return tag.isEmpty() ||
            context.resources.configuration.locales[0] == java.util.Locale.forLanguageTag(tag)
    }
}
