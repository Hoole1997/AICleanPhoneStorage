package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkIndex
import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 独立临时索引验证默认选择，不扫描、访问或删除用户文件。 */
@RunWith(AndroidJUnit4::class)
class JunkSelectionDeviceTest {
    private fun withIndex(block: (ScanIndex) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "junk_selection_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try {
            ScanIndex(isolated).use(block)
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun candidate(number: Int, bucket: String = JunkKind.TEMPORARY.name) = ScannedFile(
        uri = "file:///selection-fixtures/$number",
        name = "$number.tmp",
        mime = "application/octet-stream",
        size = if (bucket == JunkKind.EMPTY_FILES.name) 0 else 1_000,
        modifiedMillis = 1,
        category = FileCategory.OTHER,
        backend = FileBackend.DIRECT,
        scope = "/selection-fixtures",
        bucket = bucket,
    )

    @Test
    fun scanCapacityCountsOnlyInsertedCandidates() = withIndex { index ->
        val scan = index.start(CleanupFeature.SMART_CLEAN)
        val rows = listOf(candidate(0), candidate(1), candidate(2, ""), candidate(3).copy(retained = true))
        assertEquals(2_000L, index.insert(scan, rows))
        assertEquals(0L, index.insert(scan, rows))
        assertEquals(1_000L, index.insert(scan, listOf(candidate(4))))
    }

    @Test
    fun completingScanSelectsEveryPageAndCategoryButProtectsReferences() = withIndex { index ->
        val scan = index.start(CleanupFeature.SMART_CLEAN)
        val candidates = (0 until 280).map { candidate(it, JunkKind.visible[it % JunkKind.visible.size].name) }
        index.insert(scan, candidates)
        index.insert(scan, listOf(
            candidate(280, JunkKind.DUPLICATES.name).copy(retained = true),
            candidate(281, JunkKind.SIMILAR.name).copy(retained = true),
            candidate(282, ""),
        ))
        val handle = ScanHandle(scan, CleanupFeature.SMART_CLEAN, 283, "Test fixtures")
        assertNull(index.handle(scan))
        index.finishScan(handle)

        val totals = index.totals(handle, CleanupFilter())
        assertEquals(280, totals.count)
        assertEquals(totals.count, totals.selectedCount)
        assertEquals(totals.bytes, totals.selectedBytes)
        JunkIndex(index).visibleCategories(scan).forEach { assertEquals(it.count, it.selected) }
        // 超过 Pager 最大驻留量仍全部选中，不能只处理屏幕已加载的数据。
        assertTrue(index.page(handle, CleanupFilter(), 240, 60).filterNot { it.retained }.all { it.selected })
        val protected = listOf(JunkKind.DUPLICATES, JunkKind.SIMILAR).flatMap {
            index.page(handle, CleanupFilter(bucket = it.name), 0, 60)
        }.filter { it.retained }
        assertEquals(2, protected.size)
        protected.forEach {
            index.select(it.id, true)
            assertFalse(index.get(it.id)!!.selected)
        }
        index.readableDatabase.rawQuery("SELECT selected FROM files WHERE scan=? AND bucket=''", arrayOf(scan.toString())).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        val operation = index.prepareOperation(handle, CleanupFilter())
        assertEquals(280, index.operationCount(operation))
    }

    @Test
    fun rescanSelectsRemainingAndNewCandidatesWithoutResettingPreviousChoices() = withIndex { index ->
        fun finish(rows: List<ScannedFile>, feature: CleanupFeature = CleanupFeature.SMART_CLEAN): ScanHandle {
            val scan = index.start(feature)
            index.insert(scan, rows)
            return ScanHandle(scan, feature, rows.size, "Test fixtures").also(index::finishScan)
        }
        val first = finish(listOf(candidate(0), candidate(1)))
        val rows = index.page(first, CleanupFilter(), 0, 60)
        index.select(rows[0].id, false)
        index.finishScan(first)
        assertFalse(index.get(rows[0].id)!!.selected)
        // 模拟已清理项从索引移除；下一轮扫描发现剩余项和新垃圾时重新全选。
        index.remove(rows[1].id)
        val next = finish(listOf(candidate(0), candidate(2)))
        assertEquals(2, index.totals(next, CleanupFilter()).selectedCount)
        assertEquals(0, index.totals(first, CleanupFilter()).selectedCount)
        val other = finish(listOf(candidate(3)), CleanupFeature.LARGE_FILES)
        assertEquals(0, index.totals(other, CleanupFilter(minimumBytes = 0)).selectedCount)
        val empty = finish(emptyList())
        assertEquals(0, index.totals(empty, CleanupFilter()).selectedCount)
    }
}
