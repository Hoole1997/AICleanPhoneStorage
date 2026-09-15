package com.example.aicleanphonestorage

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.EmptyDirectoryDeleter
import com.example.aicleanphonestorage.feature.filecleaner.operations.FileContentAccess
import com.example.aicleanphonestorage.feature.filecleaner.scan.DocumentTreeScanner
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** ContentResolver.wrap 只连接内存测试提供者，验证真实 SAF URI/查询流程，不授予系统权限。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class JunkDocumentTreeDeviceTest {
    private data class Node(val id: String, val parent: String?, val name: String, val directory: Boolean, val size: Long = 0,
        val flags: Int = DocumentsContract.Document.FLAG_SUPPORTS_DELETE)

    private class Provider : ContentProvider() {
        val nodes = linkedMapOf(
            "0" to Node("0", null, "Shared", true),
            "1" to Node("1", "0", "cache", true),
            "2" to Node("2", "1", "payload.bin", false, 21),
            "3" to Node("3", "0", "ads", true),
            "4" to Node("4", "3", "creative.bin", false, 34),
            "5" to Node("5", "0", "parent", true),
            "6" to Node("6", "5", "child", true),
            "7" to Node("7", "0", "Download", true),
            "8" to Node("8", "7", "normal.pdf", false, 55),
        )
        val deleted = mutableListOf<String>()
        val deleteCalls = mutableListOf<String>()
        var unreadable: String? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val id = DocumentsContract.getDocumentId(uri)
            if (id == unreadable) throw IOException("Unreadable fixture")
            val rows = if (uri.lastPathSegment == "children") nodes.values.filter { it.parent == id } else listOfNotNull(nodes[id])
            val columns = projection!!.toList().toTypedArray()
            return MatrixCursor(columns).apply {
                for (row in rows) addRow(columns.map { column -> when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> row.id
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> row.name
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> if (row.directory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                    DocumentsContract.Document.COLUMN_SIZE -> row.size
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1L
                    DocumentsContract.Document.COLUMN_FLAGS -> row.flags
                    else -> null
                } })
            }
        }
        @Suppress("DEPRECATION")
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            check(method == "android:deleteDocument")
            val uri = extras!!.getParcelable<Uri>("uri")!!
            val id = DocumentsContract.getDocumentId(uri)
            deleteCalls += id
            require(nodes[id]!!.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)
            check(id != "0" && nodes.values.none { it.parent == id })
            nodes.remove(id)
            deleted += id
            return Bundle()
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    @Test fun protectedEmptyChildAlsoPreventsItsParentFromBeingACleanupCandidate() = runBlocking {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(original.cacheDir, "saf_protected_${UUID.randomUUID()}").apply { mkdirs() }
        val provider = Provider().apply {
            nodes["6"] = nodes.getValue("6").copy(flags = 0)
            nodes["9"] = Node("9", "0", "deletable", true)
        }
        val context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getContentResolver() = ContentResolver.wrap(provider)
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try { ScanIndex(context).use { index ->
            val scan = index.start(CleanupFeature.SMART_CLEAN)
            val tree = DocumentsContract.buildTreeDocumentUri("fixture.documents", "0")
            val rows = mutableListOf<ScannedFile>()
            DocumentTreeScanner(context, index) { "application/octet-stream" }.scan(tree, scan, true, { row, _ -> rows += row }) { _, _ -> }
            assertFalse(rows.any { it.name == "parent" || it.name == "child" })
            assertTrue(rows.any { it.isDirectory && it.name == "deletable" })
            assertTrue(provider.deleteCalls.isEmpty())
        } } finally { folder.deleteRecursively() }
    }

    @Test fun deletionCapabilityRevokedAfterScanIsRejectedBeforeProviderDelete() = runBlocking {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = Provider()
        val context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getContentResolver() = ContentResolver.wrap(provider)
        }
        val tree = DocumentsContract.buildTreeDocumentUri("fixture.documents", "0")
        val row = ScannedFile(uri = DocumentsContract.buildDocumentUriUsingTree(tree, "6").toString(),
            name = "child", mime = DocumentsContract.Document.MIME_TYPE_DIR, size = 0, modifiedMillis = 1,
            category = FileCategory.OTHER, backend = FileBackend.DOCUMENT, scope = tree.toString())
        provider.nodes["6"] = provider.nodes.getValue("6").copy(flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
        try { EmptyDirectoryDeleter(FileContentAccess(context)).delete(row); fail("Read/write does not imply delete") }
        catch (_: IOException) { }
        assertTrue(provider.deleteCalls.isEmpty())
        assertTrue(provider.nodes.containsKey("6"))
    }

    @Test
    fun opaqueDocumentIdsUseDirectoryNamesAndNestedDeletionChecksCurrentChildren() = runBlocking {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(original.cacheDir, "saf_fixture_${UUID.randomUUID()}").apply { mkdirs() }
        val provider = Provider()
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getContentResolver() = resolver
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try {
            ScanIndex(context).use { index ->
                val scan = index.start(CleanupFeature.SMART_CLEAN)
                val tree = DocumentsContract.buildTreeDocumentUri("fixture.documents", "0")
                val rows = mutableListOf<ScannedFile>()
                DocumentTreeScanner(context, index) { "application/octet-stream" }.scan(tree, scan, true, { row, path ->
                    JunkRules.classify(row, 0, path)?.let { rows += row.copy(bucket = it.name) }
                }) { _, _ -> }
                assertEquals(JunkKind.TEMPORARY.name, rows.single { it.name == "payload.bin" }.bucket)
                assertEquals(JunkKind.AD_FILES.name, rows.single { it.name == "creative.bin" }.bucket)
                assertFalse(rows.any { it.name == "normal.pdf" })
                val empty = rows.filter { it.isDirectory }
                assertEquals(listOf("child", "parent"), empty.map { it.name })
                assertTrue(empty.all { it.size == 0L })
                val deleter = EmptyDirectoryDeleter(FileContentAccess(context))
                provider.nodes["new"] = Node("new", "6", "new.txt", false, 9)
                try { deleter.delete(empty.first()); fail("Nonempty folder deleted") } catch (_: IOException) { }
                assertTrue(provider.deleted.isEmpty())
                provider.nodes.remove("new")
                empty.forEach { assertTrue(deleter.delete(it)) }
                assertEquals(listOf("6", "5"), provider.deleted)
                assertTrue(provider.nodes.containsKey("0"))
            }
        } finally { folder.deleteRecursively() }
    }
}
