package io.docview.push.scene

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

internal data class SceneMedia(val uri: String, val name: String, val path: String, val mime: String,
    val bytes: Long, val modified: Long, val added: Long, val pending: Boolean, val trashed: Boolean, val owner: String)

internal object SceneMediaPolicy {
    private val temporary = setOf("tmp", "temp", "part", "partial", "crdownload", "download")
    private val adTokens = setOf("ad", "ads", "admob", "applovin", "vungle", "pangle", "mintegral", "advertising")
    fun eligible(media: SceneMedia, scene: PushScene, ownPackage: String): Boolean {
        if (media.pending || media.trashed || media.bytes <= 0 || media.owner == ownPackage) return false
        val path = media.path.replace('\\', '/').lowercase(Locale.ROOT)
        val segments = path.split('/').filter { it.isNotBlank() }
        val name = media.name.lowercase(Locale.ROOT)
        if (name.startsWith('.') || name.substringAfterLast('.', "") in temporary || segments.any { it in setOf("android", "cache", "caches", "aiclean") || it in adTokens }) return false
        return when (scene) {
            PushScene.SCREENSHOT -> media.mime.startsWith("image/") && "screenshots" in segments &&
                ("pictures" in segments || "dcim" in segments)
            PushScene.DOWNLOAD -> segments.any { it == "download" || it == "downloads" } &&
                name.split(Regex("[^a-z0-9]+")).none { it in adTokens }
            else -> false
        }
    }
}

/** 仅查询可访问的新项目元数据，每次最多 64 行；没有路径遍历、图片解码或文件正文读取。 */
internal class SceneMediaReader(context: Context) {
    private val app = context.applicationContext
    fun hasAccess(scene: PushScene): Boolean {
        val allFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        return when (scene) {
            PushScene.SCREENSHOT -> allFiles || if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES)
                else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            // Android 10 的 Downloads 不能凭媒体权限读取其他应用下载；没有访问权时不绕过系统。
            PushScene.DOWNLOAD -> allFiles || (Build.VERSION.SDK_INT < 29 && granted(Manifest.permission.READ_EXTERNAL_STORAGE))
            else -> false
        }
    }
    fun collection(scene: PushScene): Uri = if (scene == PushScene.SCREENSHOT) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Files.getContentUri("external")

    suspend fun recent(scene: PushScene, startedAt: Long): List<SceneMedia> = withContext(Dispatchers.IO) {
        if (!hasAccess(scene)) return@withContext emptyList()
        val since = maxOf(startedAt / 1000, System.currentTimeMillis() / 1000 - 600)
        val folder = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val prefix = if (Build.VERSION.SDK_INT >= 29) "" else "%/"
        val paths = if (scene == PushScene.SCREENSHOT) listOf("${prefix}Pictures/Screenshots/%", "${prefix}DCIM/Screenshots/%")
            else listOf("${prefix}Download/%", "${prefix}Downloads/%")
        val selection = buildString {
            append("${MediaStore.MediaColumns.DATE_ADDED}>=? AND ${MediaStore.MediaColumns.SIZE}>0 AND ($folder LIKE ? OR $folder LIKE ?)")
            if (Build.VERSION.SDK_INT >= 29) append(" AND ${MediaStore.MediaColumns.IS_PENDING}=0")
            if (Build.VERSION.SDK_INT >= 30) append(" AND ${MediaStore.MediaColumns.IS_TRASHED}=0")
        }
        // 先由提供方筛选对应目录，再限制行数；截图突发不能挤掉下载场景的新项目。
        query(collection(scene), selection, arrayOf(since.toString(), paths[0], paths[1]))
            .filter { SceneMediaPolicy.eligible(it, scene, app.packageName) }
    }

    suspend fun valid(event: SceneEvent): Boolean = withContext(Dispatchers.IO) {
        if (!hasAccess(event.scene)) return@withContext false
        val uri = Uri.parse(event.reference)
        if (uri.scheme != "content" || uri.authority != "media" || uri.lastPathSegment?.toLongOrNull() == null) return@withContext false
        val found = query(uri, null, null).firstOrNull() ?: return@withContext false
        SceneMediaPolicy.eligible(found, event.scene, app.packageName) && found.bytes == event.bytes && found.modified == event.modified
    }

    private suspend fun query(uri: Uri, selection: String?, args: Array<String>?): List<SceneMedia> {
        val signal = CancellationSignal()
        val queryContext = currentCoroutineContext()
        val columns = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED)
        if (Build.VERSION.SDK_INT >= 29) columns += listOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        else columns += MediaStore.MediaColumns.DATA
        if (Build.VERSION.SDK_INT >= 30) columns += MediaStore.MediaColumns.IS_TRASHED
        return suspendCancellableCoroutine { continuation ->
            // 取消立即送达 ContentProvider，不能等阻塞 query 返回后才取消 CancellationSignal。
            continuation.invokeOnCancellation { signal.cancel() }
            try {
            val rows = ArrayList<SceneMedia>(64)
            val cursor = if (Build.VERSION.SDK_INT >= 26) app.contentResolver.query(uri, columns.toTypedArray(), Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns._ID))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, 64)
            }, signal) else app.contentResolver.query(uri, columns.toTypedArray(), selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT 64", signal)
            cursor?.use { c ->
                fun string(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty()
                fun long(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0
                while (rows.size < 64 && c.moveToNext()) {
                    queryContext.ensureActive()
                    val isItem = uri.lastPathSegment?.toLongOrNull() != null
                    rows += SceneMedia((if (isItem) uri else ContentUris.withAppendedId(uri, long(MediaStore.MediaColumns._ID))).toString(),
                        string(MediaStore.MediaColumns.DISPLAY_NAME).take(255),
                        string(if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA).take(512),
                        string(MediaStore.MediaColumns.MIME_TYPE), long(MediaStore.MediaColumns.SIZE), long(MediaStore.MediaColumns.DATE_MODIFIED),
                        long(MediaStore.MediaColumns.DATE_ADDED), long(MediaStore.MediaColumns.IS_PENDING) != 0L,
                        long(MediaStore.MediaColumns.IS_TRASHED) != 0L, string(MediaStore.MediaColumns.OWNER_PACKAGE_NAME))
                }
            }
            if (continuation.isActive) continuation.resume(rows)
            } catch (cancelled: CancellationException) { continuation.cancel(cancelled) }
            catch (_: RuntimeException) { if (continuation.isActive) continuation.resume(emptyList()) }
        }
    }
}
