package com.example.aicleanphonestorage.feature.filecleaner.scan

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.EnumSet
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 一次后序遍历确认空目录；只保留最多 64 层状态，错误、符号链接和截断都不能视为空。 */
internal class DirectStorageScanner(private val mime: (String) -> String) {
    private data class Directory(val path: Path, val modified: Long, var empty: Boolean = true)

    suspend fun scan(
        roots: List<String>,
        includeEmptyDirectories: Boolean,
        emit: (ScannedFile, String) -> Unit,
        progress: (Int, Int?) -> Unit,
    ): Int {
        if (Build.VERSION.SDK_INT < 30) throw SecurityException("Direct access requires Android 11")
        val context = currentCoroutineContext()
        var seen = 0
        fun report(item: ScannedFile, folder: String) {
            emit(item, folder)
            if (++seen % 50 == 0) progress(seen, null)
        }
        for (rootPath in roots) {
            val root = File(rootPath).canonicalFile.toPath()
            val stack = ArrayDeque<Directory>()
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption::class.java), 64, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    val relative = root.relativize(dir).toString().lowercase(Locale.ROOT)
                    if (relative == "android/data" || relative == "android/obb" || relative == "pictures/aiclean/compressed") {
                        stack.peekLast()?.empty = false
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    stack.addLast(Directory(dir, attrs.lastModifiedTime().toMillis()))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    stack.peekLast()?.empty = false
                    if (attrs.isRegularFile && !attrs.isSymbolicLink) {
                        val file = path.toFile()
                        val type = mime(file.name)
                        report(ScannedFile(
                            uri = Uri.fromFile(file).toString(), name = file.name, mime = type,
                            size = attrs.size(), modifiedMillis = attrs.lastModifiedTime().toMillis(),
                            category = CleanupPolicy.category(file.name, type), backend = FileBackend.DIRECT,
                            scope = root.toString(), path = file.absolutePath,
                        ), "${root.fileName}/${root.relativize(path.parent)}")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    context.ensureActive()
                    stack.peekLast()?.empty = false
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    context.ensureActive()
                    val entry = stack.removeLast()
                    val empty = entry.empty && exc == null
                    if (!empty) stack.peekLast()?.empty = false
                    // 根目录不是候选；子目录先入索引，确认清理时才能先子后父删除。
                    if (includeEmptyDirectories && empty && dir != root) {
                        val file = dir.toFile()
                        report(ScannedFile(
                            uri = Uri.fromFile(file).toString(), name = file.name,
                            mime = DocumentsContract.Document.MIME_TYPE_DIR, size = 0,
                            modifiedMillis = entry.modified, category = FileCategory.OTHER,
                            backend = FileBackend.DIRECT, scope = root.toString(), path = file.absolutePath,
                        ), "${root.fileName}/${root.relativize(dir.parent)}")
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return seen
    }
}
