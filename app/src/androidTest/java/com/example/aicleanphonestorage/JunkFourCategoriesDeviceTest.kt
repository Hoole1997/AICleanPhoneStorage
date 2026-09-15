package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 所有文件和目录都位于本测试独立缓存目录，使用生产扫描器和删除引擎验证。 */
@RunWith(AndroidJUnit4::class)
class JunkFourCategoriesDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block: suspend (File, ScanIndex, FileOperationEngine) -> Unit) = runBlocking {
        val folder = File(context.cacheDir, "four_categories_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try {
            ScanIndex(isolated).use { index ->
                val root = File(folder, "shared").apply { mkdirs() }
                val executor = (context.applicationContext as CleanApplication).container.taskExecutor
                block(root, index, FileOperationEngine(context, index, executor))
            }
        } finally { folder.deleteRecursively() }
    }

    private fun file(root: File, path: String, bytes: Int = 10) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(bytes) { 1 })
    }

    private suspend fun scan(root: File, index: ScanIndex): ScanHandle {
        val id = index.start(CleanupFeature.SMART_CLEAN)
        val batch = ArrayList<ScannedFile>(200)
        val now = System.currentTimeMillis()
        val count = FileScanSources(context, index).scan(
            ScanAccess(AccessRequest.NONE, ScanSourceKind.DIRECT, listOf(root.canonicalPath)), id,
            { row, folder ->
                if (JunkRules.include(row, folder, now)) {
                    batch += row.copy(bucket = JunkRules.classify(row, now, folder)!!.name)
                    if (batch.size == 200) { index.insert(id, batch); batch.clear() }
                }
            }, includeEmptyDirectories = true,
        ) { _, _ -> }
        index.insert(id, batch)
        return ScanHandle(id, CleanupFeature.SMART_CLEAN, count, "Test fixtures").also(index::finishScan)
    }

    @Test
    fun fourCategoriesCountOnceAndDeleteOnlySelectedCandidates() = fixture { root, index, operations ->
        file(root, "Download/setup.APK", 11)
        file(root, "app/cache/blob", 13)
        file(root, "app/cache/ads/banner.tmp", 17)
        file(root, "app/work/session.log", 19)
        val photo = file(root, "DCIM/photo.jpg", 23)
        val ordinaryEmpty = file(root, "Download/empty", 0)
        val sentinel = file(root, "keep/.nomedia", 0)
        val private = file(root, "Android/data/app/cache/private.tmp", 29)
        File(root, "empty/a/b").mkdirs()
        val handle = scan(root, index)
        val categories = JunkIndex(index).visibleCategories(handle.id).associateBy { it.kind }
        assertEquals(1, categories.getValue(JunkKind.INSTALLERS).count)
        assertEquals(2, categories.getValue(JunkKind.TEMPORARY).count)
        assertEquals(1, categories.getValue(JunkKind.AD_FILES).count)
        assertEquals(3, categories.getValue(JunkKind.EMPTY_FOLDERS).count)
        assertEquals(0L, categories.getValue(JunkKind.EMPTY_FOLDERS).bytes)
        assertEquals(60L, categories.values.sumOf { it.bytes })
        categories.values.forEach { assertEquals(it.count, it.selected) }
        val result = operations.delete(index.prepareOperation(handle, CleanupFilter())) { _, _ -> } as OperationStep.Finished
        assertEquals(7, result.summary.deleted)
        assertEquals(60L, result.summary.freedBytes)
        assertEquals(0, result.summary.failed)
        assertFalse(File(root, "empty").exists())
        assertTrue(photo.exists()); assertTrue(ordinaryEmpty.exists()); assertTrue(sentinel.exists()); assertTrue(private.exists())
        assertEquals(0, index.totals(handle, CleanupFilter()).count)
    }

    @Test
    fun changedOrUnselectedChildrenPreventParentDeletion() = fixture { root, index, operations ->
        File(root, "empty/child").mkdirs()
        val handle = scan(root, index)
        val rows = index.page(handle, CleanupFilter(bucket = JunkKind.EMPTY_FOLDERS.name), 0, 60)
        index.select(rows.single { it.name == "child" }.id, false)
        val op = index.prepareOperation(handle, CleanupFilter())
        val result = operations.delete(op) { _, _ -> } as OperationStep.Finished
        assertEquals(0, result.summary.deleted)
        assertEquals(1, result.summary.failed)
        val newFile = file(root, "empty/child/new.txt")
        index.selectAll(handle, CleanupFilter(), true)
        val changed = operations.delete(index.prepareOperation(handle, CleanupFilter())) { _, _ -> } as OperationStep.Finished
        assertEquals(0, changed.summary.deleted)
        assertTrue(newFile.exists())
    }

    @Test
    fun symlinksAndStorageRootAreNeverEmptyCandidates() = fixture { root, index, _ ->
        val outside = File(root.parentFile, "outside").apply { mkdirs() }
        val linked = File(root, "linked").apply { mkdirs() }
        Files.createSymbolicLink(File(linked, "shortcut").toPath(), outside.toPath())
        val handle = scan(root, index)
        assertEquals(0, index.totals(handle, CleanupFilter()).count)
        File(linked, "shortcut").delete()
        linked.delete()
        val emptyRoot = scan(root, index)
        assertEquals(0, index.totals(emptyRoot, CleanupFilter()).count)
    }

    @Test
    fun safDirectoryQueuePropagatesNonemptyStateAndProcessesChildrenFirst() = fixture { _, index, _ ->
        val id = index.start(CleanupFeature.SMART_CLEAN)
        val queue = DirectoryScanIndex(index)
        assertTrue(queue.enqueue(id, ScanDirectory("root")))
        assertTrue(queue.enqueue(id, ScanDirectory("parent", "root", depth = 1)))
        assertTrue(queue.enqueue(id, ScanDirectory("child", "parent", depth = 2)))
        assertFalse(queue.enqueue(id, ScanDirectory("root", "child", depth = 3)))
        while (queue.take(id) != null) { /* 模拟提供者枚举完成。 */ }
        queue.markNonempty(id, "child")
        val child = queue.take(id, completed = true)!!
        assertEquals("child", child.document)
        assertTrue(child.nonempty)
        queue.markNonempty(id, child.parent!!)
        val parent = queue.take(id, completed = true)!!
        assertEquals("parent", parent.document)
        assertTrue(parent.nonempty)
    }

    @Test
    fun hiddenLegacySelectionsAreExcludedFromCurrentCleanup() = fixture { _, index, _ ->
        val id = index.start(CleanupFeature.SMART_CLEAN)
        index.insert(id, listOf(ScannedFile(
            uri = "file:///fixture/old", name = "old", mime = "image/jpeg", size = 100, modifiedMillis = 1,
            category = FileCategory.PHOTOS, backend = FileBackend.DIRECT, scope = "/fixture", bucket = JunkKind.DUPLICATES.name,
        )))
        val handle = ScanHandle(id, CleanupFeature.SMART_CLEAN, 1, "Test fixtures").also(index::finishScan)
        index.selectAll(handle, CleanupFilter(bucket = JunkKind.DUPLICATES.name), true)
        assertEquals(1, index.totals(handle, CleanupFilter(bucket = JunkKind.DUPLICATES.name)).selectedCount)
        assertEquals(0, index.totals(handle, CleanupFilter()).selectedCount)
        assertEquals(0, index.operationCount(index.prepareOperation(handle, CleanupFilter())))
    }
}
