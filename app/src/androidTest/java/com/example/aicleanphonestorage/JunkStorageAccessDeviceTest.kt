package com.example.aicleanphonestorage

import android.content.ContextWrapper
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JunkStorageAccessDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val executor = TaskExecutor(AppDispatchers())
    private val foreignTree get() = DocumentsContract.buildTreeDocumentUri("fixture.clash.documents", "/").toString()

    @Test fun onlyLocalStorageTreesQualifyForJunk() {
        for (id in listOf("primary:", "primary:Download", "ABCD-1234:folder")) {
            val uri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", id).toString()
            assertTrue(DocumentAccessPolicy.isLocalStorageTree(uri))
        }
        for (uri in listOf(foreignTree, "file:///storage/emulated/0", "content://com.android.externalstorage.documents.evil/tree/primary%3A", ""))
            assertFalse(DocumentAccessPolicy.isLocalStorageTree(uri))
    }

    @Test fun unsupportedDocumentTreeIsSilentlyFilteredBeforeAnyProviderQuery() = runBlocking {
        val folder = File(context.cacheDir, "junk_filtered_${UUID.randomUUID()}").apply { mkdirs() }
        val provider = object : android.content.ContentProvider() {
            var queries = 0
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? { queries++; error("Filtered provider queried") }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getContentResolver() = android.content.ContentResolver.wrap(provider)
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try { ScanIndex(isolated).use { index ->
            val id = index.start(CleanupFeature.SMART_CLEAN)
            var progress: Pair<Int, Int?>? = null
            val count = FileScanSources(isolated, index).scan(
                ScanAccess(AccessRequest.NONE, ScanSourceKind.DOCUMENT, listOf(foreignTree)), id,
                { _, _ -> fail("Filtered folder became a candidate") }, includeEmptyDirectories = true,
                directories = DocumentAccessPolicy,
            ) { done, total -> progress = done to total }
            val handle = ScanHandle(id, CleanupFeature.SMART_CLEAN, count, "Selected folder").also(index::finishScan)
            assertEquals(0, provider.queries)
            assertEquals(0 to 0, progress)
            assertEquals(0, index.totals(handle, CleanupFilter()).count)
        } } finally { folder.deleteRecursively() }
    }

    @Test fun cachedForeignJunkCannotReachProviderEvenWhenRestoredAfterUpgrade() = runBlocking {
        val folder = File(context.cacheDir, "junk_cached_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String) = File(folder, name)
            override fun getContentResolver(): android.content.ContentResolver = error("Foreign provider must not be accessed")
        }
        // FileContentAccess 保存 resolver 引用，因此使用独立提供者计数验证没有发生实际 query/delete。
        val provider = object : android.content.ContentProvider() {
            var calls = 0
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? { calls++; return null }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { calls++; return 0 }
        }
        val providerContext = object : ContextWrapper(isolated) {
            override fun getApplicationContext() = this
            override fun getContentResolver() = android.content.ContentResolver.wrap(provider)
        }
        try { ScanIndex(providerContext).use { index ->
            val scan = index.start(CleanupFeature.SMART_CLEAN)
            val tree = Uri.parse(foreignTree)
            index.insert(scan, listOf(ScannedFile(
                uri = DocumentsContract.buildDocumentUriUsingTree(tree, "//fixture/providers").toString(),
                name = "providers", mime = DocumentsContract.Document.MIME_TYPE_DIR, size = 0,
                modifiedMillis = 1, category = FileCategory.OTHER, backend = FileBackend.DOCUMENT,
                scope = foreignTree, bucket = JunkKind.EMPTY_FOLDERS.name)))
            val handle = ScanHandle(scan, CleanupFeature.SMART_CLEAN, 1, "Selected folder").also(index::finishScan)
            val op = index.prepareOperation(handle, CleanupFilter())
            val result = FileOperationEngine(providerContext, index, executor).delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(0, result.summary.deleted)
            assertEquals(1, result.summary.failed)
            assertEquals(0, provider.calls)
        } } finally { folder.deleteRecursively() }
    }

    @Test fun schemaFourUpgradeKeepsSelectionsAndTreatsUnknownDirectoryFlagsConservatively() {
        val folder = File(context.cacheDir, "junk_upgrade_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        var handle: ScanHandle? = null
        var operation = 0L
        try {
            ScanIndex(isolated).use { index ->
                val scan = index.start(CleanupFeature.LARGE_FILES)
                index.insert(scan, listOf(ScannedFile(uri = "file:///fixture.pdf", name = "fixture.pdf", mime = "application/pdf",
                    size = 20_000_000, modifiedMillis = 1, category = FileCategory.DOCUMENTS, backend = FileBackend.DIRECT, scope = "/")))
                handle = ScanHandle(scan, CleanupFeature.LARGE_FILES, 1, "Fixture").also(index::finishScan)
                operation = index.prepareOperation(handle!!, CleanupFilter())
                index.selectAll(handle!!, CleanupFilter(), false)
                val db = index.writableDatabase
                db.execSQL("DROP TABLE directories")
                db.execSQL("""CREATE TABLE directories(scan INTEGER NOT NULL, document TEXT NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0, parent TEXT, name TEXT NOT NULL DEFAULT '', folder TEXT NOT NULL DEFAULT '',
                    modified INTEGER NOT NULL DEFAULT 0, depth INTEGER NOT NULL DEFAULT 0, nonempty INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(scan,document))""")
                db.execSQL("CREATE INDEX directories_work ON directories(scan,done,depth)")
                db.execSQL("INSERT INTO directories(scan,document) VALUES (?,?)", arrayOf(scan, "old-directory"))
                db.version = 4
            }
            ScanIndex(isolated).use { index ->
                assertEquals(5, index.readableDatabase.version)
                assertEquals(0, index.totals(handle!!, CleanupFilter()).selectedCount)
                assertEquals(1, index.operationCount(operation))
                assertEquals(0, DirectoryScanIndex(index).take(handle!!.id)!!.flags)
            }
        } finally { folder.deleteRecursively() }
    }
}
