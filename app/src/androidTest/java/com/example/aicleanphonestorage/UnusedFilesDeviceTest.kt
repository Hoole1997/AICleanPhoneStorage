package com.example.aicleanphonestorage

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.*
import com.example.aicleanphonestorage.feature.unused.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只操作缓存夹样例和内存 DocumentsProvider；不卸载应用，不触碰真实 Android/data。 */
@RunWith(AndroidJUnit4::class)
class UnusedFilesDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Packages : UnusedPackages {
        override var completeVisibility = true
        val installed = mutableSetOf("com.keep.app")
        override fun installed(packageName: String): Boolean? = if (packageName in installed) true else if (completeVisibility) false else null
    }
    private val executor = TaskExecutor(AppDispatchers())

    @Test fun realArchiveMetadataAndFreshPackageCheckProtectChangedCandidates() = runBlocking {
        val root = File(context.cacheDir, "unused_apk_${UUID.randomUUID()}").apply { mkdirs() }.canonicalFile
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(root, name)
        }
        try {
            val apk = File(root, "installed.apk")
            File(context.applicationInfo.sourceDir).copyTo(apk)
            val row = ScannedFile(uri = Uri.fromFile(apk).toString(), name = apk.name,
                mime = "application/vnd.android.package-archive", size = apk.length(), modifiedMillis = apk.lastModified(),
                category = FileCategory.APK, backend = FileBackend.DIRECT, scope = root.path, path = apk.path)
            FileContentAccess(context).validate(row)
            assertNotNull("Fixture must be a readable APK", context.packageManager.getPackageArchiveInfo(apk.path, 0))
            assertEquals(UnusedKind.INSTALLED_APK, UnusedClassifier(context).classify(row, System.currentTimeMillis()))
            val packages = Packages().apply { installed += context.packageName }
            ScanIndex(isolated).use { index ->
                val scan = index.start(CleanupFeature.UNUSED_FILES)
                index.insert(scan, listOf(row.copy(bucket = UnusedKind.INSTALLED_APK.bucket)))
                val handle = ScanHandle(scan, CleanupFeature.UNUSED_FILES, 1, "Test fixtures").also(index::finishScan)
                assertEquals(1, index.totals(handle, CleanupFilter()).selectedCount) // 新 APK 不受 30 天条件过滤。
                val op = index.prepareOperation(handle, CleanupFilter())
                packages.installed.clear()
                val result = FileOperationEngine(isolated, index, executor, packages).delete(op) { _, _ -> } as OperationStep.Finished
                assertEquals(0, result.summary.deleted)
                assertTrue(apk.exists())
            }
            apk.writeText("invalid apk")
            assertNull(UnusedClassifier(context).classify(row.copy(size = apk.length(), modifiedMillis = apk.lastModified()), System.currentTimeMillis()))
        } finally { root.deleteRecursively() }
    }

    private data class Node(val path: String, val directory: Boolean, val size: Long = 0,
        val modified: Long = System.currentTimeMillis() - 31L * 86_400_000)
    private class Provider : ContentProvider() {
        val nodes = linkedMapOf<String, Node>()
        val queried = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        init {
            listOf(Node("", true), Node("Android", true), Node("Android/data", true),
                Node("Android/data/com.old.app", true), Node("Android/data/com.old.app/cache", true),
                Node("Android/data/com.old.app/cache/blob", false, 7),
                Node("Android/data/com.keep.app", true), Node("Android/data/com.keep.app/private", false, 19),
                Node("Download", true), Node("Download/old.pdf", false, 11), Node("Download/old.jpg", false, 13),
                Node("Download/new.zip", false, 17, System.currentTimeMillis()),
                Node("Documents", true), Node("Documents/old.pdf", false, 23)).forEach { nodes[it.path] = it }
        }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val path = DocumentsContract.getDocumentId(uri).substringAfter(':')
            queried += path
            val rows = if (uri.lastPathSegment == "children") nodes.values.filter {
                it.path.isNotEmpty() && it.path.substringBeforeLast('/', "") == path
            } else listOfNotNull(nodes[path])
            val columns = projection!!.toList().toTypedArray()
            return MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { when (it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "primary:${row.path}"
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> row.path.substringAfterLast('/').ifEmpty { "Shared" }
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (row.directory) DocumentsContract.Document.MIME_TYPE_DIR else when (row.path.substringAfterLast('.')) {
                    "jpg" -> "image/jpeg"; "pdf" -> "application/pdf"; else -> "application/octet-stream"
                }
                DocumentsContract.Document.COLUMN_SIZE -> row.size
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> row.modified
                DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                else -> null
            } }) } }
        }
        @Suppress("DEPRECATION") override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            check(method == "android:deleteDocument")
            val path = DocumentsContract.getDocumentId(extras!!.getParcelable<Uri>("uri")!!).substringAfter(':')
            check(path.isNotEmpty() && nodes.keys.none { it.startsWith("$path/") })
            nodes.remove(path); deleted += path
            return Bundle()
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Test fun safGroupsExcludeInstalledPrivateMediaAndRecentFilesAndDeleteChildrenBeforeParents() = saf(false)
    @Test fun appReinstalledAfterScanProtectsItsEntireResidualTree() = saf(true)
    private fun saf(reinstall: Boolean) = runBlocking {
        val root = File(context.cacheDir, "unused_saf_${UUID.randomUUID()}").apply { mkdirs() }
        val provider = Provider()
        val resolver = ContentResolver.wrap(provider)
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getContentResolver() = resolver
            override fun getDatabasePath(name: String) = File(root, name)
        }
        val packages = Packages()
        val classifier = UnusedClassifier(isolated, packages)
        try { ScanIndex(isolated).use { index ->
            val scan = index.start(CleanupFeature.UNUSED_FILES)
            val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:")
            val rows = mutableListOf<ScannedFile>()
            DocumentTreeScanner(isolated, index) { "application/octet-stream" }.scan(tree, scan, true, { row, _ ->
                classifier.classify(row, System.currentTimeMillis())?.let { rows += row.copy(bucket = it.bucket) }
            }, classifier) { _, _ -> }
            assertFalse(provider.queried.any { it.startsWith("Android/data/com.keep.app") })
            assertEquals(setOf("blob", "cache", "com.old.app", "old.pdf"), rows.map { it.name }.toSet())
            index.insert(scan, rows)
            val handle = ScanHandle(scan, CleanupFeature.UNUSED_FILES, rows.size, "Test fixtures").also(index::finishScan)
            assertEquals(4, index.totals(handle, CleanupFilter()).selectedCount)
            assertEquals(18L, index.totals(handle, CleanupFilter()).bytes)
            assertEquals(7L, index.unusedGroups(handle).single { it.kind == UnusedKind.RESIDUE }.totals.bytes)
            val op = index.prepareOperation(handle, CleanupFilter())
            if (reinstall) packages.installed += "com.old.app"
            val result = FileOperationEngine(isolated, index, executor, packages).delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(if (reinstall) 1 else 4, result.summary.deleted)
            assertTrue(provider.nodes.containsKey("Download/old.jpg"))
            assertTrue(provider.nodes.containsKey("Android/data/com.keep.app/private"))
            if (!reinstall) assertTrue(provider.deleted.indexOf("Android/data/com.old.app/cache/blob") < provider.deleted.indexOf("Android/data/com.old.app"))
            else assertTrue(provider.nodes.containsKey("Android/data/com.old.app/cache/blob"))
            packages.completeVisibility = false
            assertFalse(classifier.visitDirectory(DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Android/data")))
        } } finally { root.deleteRecursively() }
    }
}
