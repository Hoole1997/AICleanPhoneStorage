package io.docview.push.scene

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import io.docview.push.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.security.MessageDigest

/** Service 内的包广播、媒体 ContentObserver、DownloadManager 完成通知；只查询可见元数据。 */
internal class FileSceneSources(
    private val context: Context,
    private val scope: CoroutineScope,
    private val storage: SceneStorage,
    private val config: () -> SceneConfiguration,
    private val now: () -> SceneMoment,
    private val offer: (SceneEvent) -> Unit,
) : AutoCloseable {
    private val media = SceneMediaReader(context)
    private val packages = ScenePackageCatalog(context, storage)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = mutableMapOf<PushScene, ContentObserver>()
    private val observedAfter = mutableMapOf<PushScene, Long>()
    private val mediaSignals = Channel<PushScene>(16, BufferOverflow.DROP_OLDEST)
    private data class PackageSignal(val name: String, val added: Boolean, val replacing: Boolean, val removedData: Boolean)
    private val packageSignals = Channel<PackageSignal>(64, BufferOverflow.DROP_OLDEST)
    private val downloadSignals = Channel<Long>(16, BufferOverflow.DROP_OLDEST)
    // 仅下载使用此缓存；保留旧存储键，截图不再查询或写入去重记录。
    private val seenDownloads = LinkedHashSet<String>()
    private val downloads = DownloadSceneBatch()
    private var packageReceiver: BroadcastReceiver? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var closed = false
    private var catalogReady = false

    init {
        scope.launch {
            for (signal in packageSignals) {
                if (closed) break
                if (!config().enabled(PushScene.APP_INSTALL) && !config().enabled(PushScene.APP_UNINSTALL)) continue
                try {
                val clock = now()
                Logger.d("场景推送：收到${if (signal.added) "应用安装" else "应用卸载"}事件，是否替换更新=${if (signal.replacing) "是" else "否"}")
                if (signal.added) {
                    val label = packages.installed(signal.name) ?: run { Logger.d("场景推送：应用安装事件未通过用户应用核验，本次跳过"); continue }
                    if (!signal.replacing && config().enabled(PushScene.APP_INSTALL))
                        offer(SceneEvent(PushScene.APP_INSTALL, clock.wall, clock.elapsed + SCENE_CONFIRMATION_DELAY_MS, signal.name, label))
                } else if (!signal.replacing && signal.removedData && config().enabled(PushScene.APP_UNINSTALL)) {
                    val label = packages.removedName(signal.name) ?: run { Logger.d("场景推送：应用卸载事件缺少可确认的原应用记录，本次跳过"); continue }
                    offer(SceneEvent(PushScene.APP_UNINSTALL, clock.wall, clock.elapsed + SCENE_CONFIRMATION_DELAY_MS, signal.name, label))
                }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Logger.w("场景推送：应用事件核验异常，本次跳过，异常类型=${error.javaClass.simpleName}") }
            }
        }
        scope.launch {
            try { storage.cached("seen_media")?.let { raw ->
                runCatching { com.google.gson.JsonParser.parseString(raw).asJsonArray }.getOrNull()?.take(512)
                    ?.forEach { if (it.isJsonPrimitive && it.asString.length == 64) seenDownloads += it.asString }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* 损坏的去重缓存不终止新的、已授权元数据事件。 */ }
            for (scene in mediaSignals) {
                if (closed) break
                if (!config().enabled(scene)) continue
                try {
                val since = observedAfter[scene] ?: continue
                val rows = media.recent(scene, since)
                Logger.d("场景推送：${scene.logName} 收到媒体变化，符合目录和元数据条件的项目数=${rows.size}")
                if (observedAfter[scene] != since || !config().enabled(scene)) continue
                when (scene) {
                    PushScene.SCREENSHOT -> ScreenshotSceneRules.onMediaChanged(rows, now())?.let {
                        Logger.d("场景推送：${scene.logName} 识别成功，无额外等待，交由远程截图间隔判断后进入展示队列")
                        offer(it)
                    }
                    PushScene.DOWNLOAD -> handleDownloads(rows)
                    else -> Unit
                }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Logger.w("场景推送：${scene.logName} 媒体事件处理失败，异常类型=${error.javaClass.simpleName}") }
            }
        }
        scope.launch {
            for (id in downloadSignals) {
                if (!config().enabled(PushScene.DOWNLOAD)) continue
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id))?.use {
                            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                        } == true
                    } catch (_: RuntimeException) { false }
                }
                // DownloadManager 只信任可查询的成功任务；其他应用的全局下载仍以已授权 MediaStore 为准。
                if (success) { Logger.d("场景推送：下载完成状态核验成功，开始检查可访问的下载元数据"); mediaSignals.trySend(PushScene.DOWNLOAD) }
                else Logger.d("场景推送：下载完成广播未通过成功状态核验，本次跳过")
            }
        }
    }

    /** 下载保留去重与聚合；完成确认按统一一分钟执行，与截图的无固定等待规则分离。 */
    private suspend fun handleDownloads(rows: List<SceneMedia>) {
        var changed = false
        var duplicates = 0
        for (row in rows.asReversed()) {
            currentCoroutineContext().ensureActive()
            val key = withContext(Dispatchers.Default) {
                MessageDigest.getInstance("SHA-256").digest("${row.uri}:${row.added}".toByteArray()).joinToString("") { "%02x".format(it) }
            }
            if (!seenDownloads.add(key)) { duplicates++; continue }
            changed = true
            while (seenDownloads.size > 512) seenDownloads.remove(seenDownloads.first())
            val clock = now()
            val event = SceneEvent(PushScene.DOWNLOAD, row.added * 1000, clock.elapsed + SCENE_CONFIRMATION_DELAY_MS, row.uri,
                bytes = row.bytes, modified = row.modified)
            val batch = downloads.add(event)
            if (batch != null) offer(batch)
            else Logger.d("场景推送：新增下载已纳入小文件聚合，本次尚不生成触发候选")
        }
        if (duplicates > 0) Logger.d("场景推送：${PushScene.DOWNLOAD.logName} 已过滤重复媒体项目，数量=$duplicates")
        if (changed) storage.cache("seen_media", Gson().toJson(seenDownloads))
    }

    suspend fun refresh(configuration: SceneConfiguration) {
        if (closed) return
        val packageEnabled = configuration.enabled(PushScene.APP_INSTALL) || configuration.enabled(PushScene.APP_UNINSTALL)
        if (!packageEnabled) stopPackages() else if (packageReceiver == null) {
            if (!catalogReady) {
                try { packages.initialize(); catalogReady = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Logger.w("场景推送：用户应用快照初始化失败，无法确认的卸载事件将跳过，异常类型=${error.javaClass.simpleName}") }
            }
            if (closed) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (packageReceiver !== this || closed) return
                    val name = intent.data?.schemeSpecificPart ?: return
                    if (intent.data?.scheme != "package" || name == context.packageName || !ScenePackageCatalog.validPackage(name)) return
                    val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
                    if (uid < 0 || UserHandle.getUserHandleForUid(uid) != Process.myUserHandle()) return
                    packageSignals.trySend(PackageSignal(name, intent.action == Intent.ACTION_PACKAGE_ADDED,
                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false), intent.getBooleanExtra(Intent.EXTRA_DATA_REMOVED, false)))
                }
            }
            packageReceiver = receiver
            try { ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
            }, ContextCompat.RECEIVER_NOT_EXPORTED); Logger.d("场景推送：应用安装与卸载广播监听已注册") }
            catch (error: RuntimeException) { packageReceiver = null; Logger.w("场景推送：应用广播注册失败，异常类型=${error.javaClass.simpleName}") }
        }
        for (scene in listOf(PushScene.SCREENSHOT, PushScene.DOWNLOAD)) {
            val accessible = configuration.enabled(scene) && withContext(Dispatchers.IO) { media.hasAccess(scene) }
            if (closed) return
            if (!accessible) {
                Logger.d("场景推送：${scene.logName} 暂不监听，场景开关关闭或缺少访问权限")
                stopObserver(scene)
            } else if (scene !in observers) {
                val observer = object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        if (!selfChange && !closed && observers[scene] === this) mediaSignals.trySend(scene)
                    }
                }
                try {
                    context.contentResolver.registerContentObserver(media.collection(scene), true, observer)
                    observers[scene] = observer
                    // 授权或重新开启后只接受新的变化，不补发关闭/无权限期间的历史文件。
                    observedAfter[scene] = now().wall
                    Logger.d("场景推送：${scene.logName} 媒体监听已注册，只处理后续新增项目")
                }
                catch (error: RuntimeException) { Logger.w("场景推送：${scene.logName} 媒体监听注册失败，异常类型=${error.javaClass.simpleName}") }
            }
        }
        if (PushScene.DOWNLOAD !in observers) stopDownloads() else if (downloadReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (downloadReceiver !== this || closed) return
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 }?.let(downloadSignals::trySend)
                }
            }
            downloadReceiver = receiver
            try { ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED); Logger.d("场景推送：下载完成广播监听已注册") }
            catch (error: RuntimeException) { downloadReceiver = null; Logger.w("场景推送：下载广播注册失败，异常类型=${error.javaClass.simpleName}") }
        }
    }

    suspend fun valid(event: SceneEvent) = when (event.scene) {
        PushScene.SCREENSHOT, PushScene.DOWNLOAD -> media.valid(event)
        PushScene.APP_INSTALL, PushScene.APP_UNINSTALL -> packages.validate(event)
        else -> false
    }
    private fun stopObserver(scene: PushScene) { observedAfter.remove(scene); observers.remove(scene)?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } } }
    private fun stopPackages() { val old = packageReceiver; packageReceiver = null; catalogReady = false; old?.let { runCatching { context.unregisterReceiver(it) } } }
    private fun stopDownloads() { val old = downloadReceiver; downloadReceiver = null; old?.let { runCatching { context.unregisterReceiver(it) } }; downloads.clear() }
    override fun close() {
        Logger.d("场景推送：释放应用、媒体与下载监听")
        closed = true; stopPackages(); stopDownloads()
        observers.keys.toList().forEach(::stopObserver)
        mediaSignals.close(); packageSignals.close(); downloadSignals.close()
    }
}
