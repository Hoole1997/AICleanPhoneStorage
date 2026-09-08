package com.example.aicleanphonestorage.feature.filecleaner.operations

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/** 操作前重新核对范围/大小/修改时间，拒绝将扫描后被替换的文件当作原选择删除。 */
internal class FileContentAccess(context: Context) {
    val app = context.applicationContext
    val resolver = app.contentResolver

    fun input(item: ScannedFile): InputStream =
        if (item.backend == FileBackend.DIRECT) FileInputStream(validatedFile(item))
        else resolver.openInputStream(Uri.parse(item.uri)) ?: throw IOException("Cannot open file")

    fun validatedFile(item: ScannedFile): File {
        val root = File(item.scope).canonicalFile
        val file = File(item.path)
        val canonical = file.canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(canonical.path.startsWith(prefix) && canonical.path == file.absolutePath) {
            "File outside authorized root"
        }
        val relative = canonical.path.removePrefix(prefix).lowercase()
        require(!relative.startsWith("android/data/") && !relative.startsWith("android/obb/")) {
            "Private app directory"
        }
        if (Build.VERSION.SDK_INT >= 26 && Files.isSymbolicLink(file.toPath()))
            throw IOException("Symbolic link skipped")
        if (!file.isFile) throw IOException("File missing")
        return file
    }

    fun validate(item: ScannedFile) {
        val current =
            if (item.backend == FileBackend.DIRECT)
                validatedFile(item).let { it.length() to it.lastModified() }
            else {
                val size =
                    if (item.backend == FileBackend.MEDIA) MediaStore.MediaColumns.SIZE
                    else DocumentsContract.Document.COLUMN_SIZE
                val modified =
                    if (item.backend == FileBackend.MEDIA) MediaStore.MediaColumns.DATE_MODIFIED
                    else DocumentsContract.Document.COLUMN_LAST_MODIFIED
                val mime =
                    if (item.backend == FileBackend.MEDIA) MediaStore.MediaColumns.MIME_TYPE
                    else DocumentsContract.Document.COLUMN_MIME_TYPE
                resolver
                    .query(Uri.parse(item.uri), arrayOf(size, modified, mime), null, null, null)
                    ?.use {
                        if (!it.moveToFirst()) throw IOException("File missing")
                        if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                            throw IOException("Not a file")
                        it.getLong(0) to
                            (it.getLong(1) * if (item.backend == FileBackend.MEDIA) 1000 else 1)
                    } ?: throw IOException("File unavailable")
            }
        if (
            current.first != item.size ||
                (item.modifiedMillis > 0 &&
                    current.second > 0 &&
                    current.second != item.modifiedMillis)
        )
            throw IOException("File changed since scan")
    }
}
