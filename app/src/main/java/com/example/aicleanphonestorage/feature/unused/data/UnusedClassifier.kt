package com.example.aicleanphonestorage.feature.unused.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** 仅在扫描/操作的 I/O 协程内使用。无法证明包已卸载时不产生残留候选。 */
internal class UnusedClassifier(context: Context, private val packages: UnusedPackages = AndroidUnusedPackages(context),
    private val checkCancelled: () -> Unit = {}) : com.example.aicleanphonestorage.feature.filecleaner.scan.DirectoryScanPolicy {
    private val app = context.applicationContext
    private val content = FileContentAccess(app)
    private val archiveParser = app.packageManager

    fun relativePath(file: ScannedFile): String? = when (file.backend) {
        FileBackend.DIRECT -> runCatching { File(file.path).relativeTo(File(file.scope)).invariantSeparatorsPath }.getOrNull()
        FileBackend.DOCUMENT -> documentPath(Uri.parse(file.uri))
        FileBackend.MEDIA -> null
    }

    // 仅系统 ExternalStorageProvider 的卷内路径具有此格式，不猜测其他提供者的不透明 documentId。
    private fun documentPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        return runCatching {
            val id = DocumentsContract.getDocumentId(uri)
            id.substringAfter(':', "").takeIf { ':' in id && it.split('/').none { part -> part == ".." || part == "." } }
        }.getOrNull()
    }

    private fun absent(owner: String): Boolean {
        if (owner == app.packageName) return false
        // Android 11+ 查询结果有可见性过滤；NameNotFound 不能证明已卸载。也不扩大应用查询权限。
        return packages.completeVisibility && packages.installed(owner) == false
    }

    override fun visitDirectory(uri: Uri): Boolean {
        val path = documentPath(uri) ?: return true
        if (path == "Android/data" || path == "Android/obb") return packages.completeVisibility
        val owner = UnusedRules.residualOwner(path) ?: return true
        return absent(owner)
    }

    override fun includeNonemptyDirectory(uri: Uri): Boolean =
        documentPath(uri)?.let(UnusedRules::residualOwner)?.let(::absent) == true

    fun classify(file: ScannedFile, now: Long): UnusedKind? {
        checkCancelled()
        val path = relativePath(file)
        val owner = path?.let(UnusedRules::residualOwner)
        if (owner != null) return UnusedKind.RESIDUE.takeIf { absent(owner) }
        if (file.isDirectory || file.size <= 0) return null
        if (file.category == FileCategory.APK)
            return UnusedKind.INSTALLED_APK.takeIf { installedArchive(file) }
        return UnusedKind.DOWNLOAD.takeIf { path != null && UnusedRules.download(file, path, now) }
    }

    private fun installedArchive(file: ScannedFile): Boolean {
        var temporary: File? = null
        try {
            content.validate(file)
            val archive = if (file.backend == FileBackend.DIRECT) content.validatedFile(file)
            else {
                // PackageManager 要求本地 APK 路径。SAF 串行流式复制并限制磁盘占用，不把 APK 装入内存。
                if (file.size > MAX_ARCHIVE_BYTES) return false
                File.createTempFile("unused_apk_", ".apk", app.cacheDir).also { target ->
                    temporary = target
                    content.input(file).use { input -> target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        while (true) {
                            checkCancelled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > MAX_ARCHIVE_BYTES) throw IOException("Archive limit exceeded")
                            output.write(buffer, 0, count)
                        }
                    } }
                }
            }
            val parsed = archiveParser.getPackageArchiveInfo(archive.path, 0) ?: return false
            // 用户规则为包名已安装即可；版本更高的包也按同一明确规则作为候选，最终由用户确认。
            if (packages.installed(parsed.packageName) != true) return false
            content.validate(file)
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { return false }
        catch (_: PackageManager.NameNotFoundException) { return false }
        catch (_: RuntimeException) { return false }
        finally { temporary?.delete() }
    }

    companion object { private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024 }
}
