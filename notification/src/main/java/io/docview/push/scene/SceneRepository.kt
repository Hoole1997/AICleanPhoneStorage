package io.docview.push.scene

import android.content.Context
import io.docview.push.NotificationHost
import io.docview.push.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SceneStorage(context: Context) : SceneHistoryStore {
    private val app = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences("scene_push", Context.MODE_PRIVATE) }
    private val userPrefs by lazy { app.getSharedPreferences("scene_push_choices", Context.MODE_PRIVATE) }
    private val writes = Mutex()
    override suspend fun history(scene: PushScene) = withContext(Dispatchers.IO) {
        SceneHistory(prefs.getLong("${scene.code}.wall", 0), prefs.getLong("${scene.code}.elapsed", 0),
            prefs.getInt("${scene.code}.boot", -1), prefs.getString("${scene.code}.copy", "").orEmpty())
    }
    override suspend fun succeeded(scene: PushScene, moment: SceneMoment, copyId: String) = withContext(Dispatchers.IO) {
        writes.withLock {
            check(prefs.edit().putLong("${scene.code}.wall", moment.wall).putLong("${scene.code}.elapsed", moment.elapsed)
                .putInt("${scene.code}.boot", moment.boot).putString("${scene.code}.copy", copyId).commit())
        }
    }
    suspend fun cached(key: String) = withContext(Dispatchers.IO) { prefs.getString(key, null) }
    suspend fun cache(key: String, value: String) = withContext(Dispatchers.IO) {
        require(value.length <= SceneConfig.MAX_JSON)
        writes.withLock { check(prefs.edit().putString(key, value).commit()) }
    }
    suspend fun choices() = withContext(Dispatchers.IO) {
        SceneChoices(userPrefs.getBoolean("user.enabled", true), PushScene.entries.filterNot { userPrefs.getBoolean("user.${it.code}", true) }.toSet())
    }
    suspend fun choose(scene: PushScene?, enabled: Boolean) = withContext(Dispatchers.IO) {
        writes.withLock { check(userPrefs.edit().putBoolean(scene?.let { "user.${it.code}" } ?: "user.enabled", enabled).commit()) }
    }
}

/** 独立的线上→有效缓存→本地资源链路；只保存八个规则和有界文案，不复用/修改旧池与旧频控。 */
internal class SceneRepository(context: Context, private val host: NotificationHost) {
    private val app = context.applicationContext
    val storage = SceneStorage(app)
    private val mutex = Mutex()
    private val current = MutableStateFlow<SceneConfiguration?>(null)
    val state = current.asStateFlow()
    val snapshot get() = requireNotNull(current.value)

    suspend fun initialize() { if (current.value == null) refresh() }

    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock {
        val defaults = withContext(Dispatchers.IO) {
            val policy = app.assets.open("scene_push_config.json").bufferedReader().use { SceneConfig.policy(it.readText()) }
            val copies = app.assets.open("scene_push_content.json").bufferedReader().use { SceneCopies.parse(it.readText()) }
            policy to copies
        }
        suspend fun <T> resolve(key: String, fallback: T, parse: (String) -> T): T {
            val remote = try { host.scenePushParameter(key) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Logger.w("场景推送：远程参数读取失败，参数=$key，异常类型=${error.javaClass.simpleName}"); null }
            if (!remote.isNullOrBlank() && remote.length <= SceneConfig.MAX_JSON) {
                val parsed = runCatching { parse(remote) }.getOrNull()
                if (parsed != null) { storage.cache(key, remote); Logger.d("场景推送：采用有效远程配置，参数=$key"); return parsed }
                Logger.w("场景推送：远程参数格式无效，尝试缓存或本地默认，参数=$key")
            }
            val cached = storage.cached(key)?.let { runCatching { parse(it) }.getOrNull() }
            Logger.d("场景推送：采用${if (cached != null) "有效缓存" else "本地默认"}，参数=$key")
            return cached ?: fallback
        }
        val policy = resolve(ScenePushKeys.CONFIG, defaults.first, SceneConfig::policy)
        val copies = resolve(ScenePushKeys.CONTENT, defaults.second) { SceneCopies.parse(it, defaults.second) }
        current.value = SceneConfiguration(policy, copies, storage.choices())
        Logger.d("场景推送：配置刷新完成，启用场景数量=${PushScene.entries.count(snapshot::enabled)}，各场景独立间隔单位为分钟")
    } }

    suspend fun choose(scene: PushScene?, enabled: Boolean) = mutex.withLock {
        storage.choose(scene, enabled)
        Logger.d("场景推送：用户设置已保存，范围=${scene?.logName ?: "全部新场景"}，开关=${if (enabled) "开启" else "关闭"}")
        current.value?.let { current.value = it.copy(choices = storage.choices()) }
    }
}
