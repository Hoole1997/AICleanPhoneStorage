package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.home.data.*
import com.example.aicleanphonestorage.feature.home.preview.HomePreviewSupport
import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkIndex
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 聚合/身份关联使用独立临时数据库；系统统计仅只读查询，不修改权限或用户文件。 */
@RunWith(AndroidJUnit4::class)
class HomeMetricsDeviceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val executor
        get() = (context.applicationContext as CleanApplication).container.taskExecutor

    private fun isolated(block: (ScanIndex) -> Unit) {
        val root = File(context.cacheDir, "metrics_test_${UUID.randomUUID()}").apply { mkdirs() }
        val wrapper =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getDatabasePath(name: String) = File(root, name)
            }
        try {
            ScanIndex(wrapper).use(block)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun file(
        uri: String = "file:///fixture/shared.jpg",
        path: String = "/fixture/shared.jpg",
        bytes: Long = 12_000_000,
    ) =
        ScannedFile(
            uri = uri,
            name = "shared.jpg",
            mime = "image/jpeg",
            size = bytes,
            modifiedMillis = 1,
            category = FileCategory.PHOTOS,
            backend = FileBackend.DIRECT,
            scope = "/fixture",
            path = path,
        )

    private fun scan(
        index: ScanIndex,
        feature: CleanupFeature,
        files: List<ScannedFile>,
        ready: Boolean = true,
    ): ScanHandle {
        val id = index.start(feature)
        index.insert(id, files)
        return ScanHandle(id, feature, files.size, "fixture").also {
            if (ready) index.finishScan(it)
        }
    }

    @Test
    fun latestCompletedScanWinsAndNeverScannedDiffersFromEmpty() = isolated { index ->
        val source = HomeFileMetricsSource(index, executor)
        assertEquals(HomeToolMetric.NotScanned, source.read().largeFiles)
        scan(index, CleanupFeature.LARGE_FILES, listOf(file(bytes = 20_000_000)))
        val latest = scan(index, CleanupFeature.LARGE_FILES, listOf(file(bytes = 12_000_000)))
        scan(index, CleanupFeature.LARGE_FILES, listOf(file(bytes = 99_000_000)), ready = false)
        scan(index, CleanupFeature.SCREENSHOTS, emptyList())
        assertEquals(HomeToolMetric.Bytes(12_000_000), source.read().largeFiles)
        assertEquals(HomeToolMetric.Bytes(0), source.read().screenshots)
        assertEquals(HomeToolMetric.NotScanned, source.read().compress)
        index.selectAll(latest, CleanupFilter(), true)
        assertEquals(HomeToolMetric.Bytes(12_000_000), source.read().largeFiles)
    }

    @Test
    fun deletingOneIdentityUpdatesEveryAffectedEntry() = isolated { index ->
        val source = HomeFileMetricsSource(index, executor)
        val large = scan(index, CleanupFeature.LARGE_FILES, listOf(file()))
        scan(index, CleanupFeature.UNUSED_FILES, listOf(file()))
        scan(
            index,
            CleanupFeature.PHOTO_COMPRESS,
            listOf(file(uri = "content://media/external/images/media/123")),
        )
        val row = index.page(large, CleanupFilter(), 0, 60).single()
        index.remove(row.id)
        assertEquals(HomeToolMetric.Bytes(0), source.read().largeFiles)
        assertEquals(HomeToolMetric.Bytes(0), source.read().unusedFiles)
        assertEquals(HomeToolMetric.Bytes(0), source.read().compress)
    }

    @Test
    fun deletingReferenceThroughAnotherEntryProtectsRemainingCopy() = isolated { index ->
        val large = scan(index, CleanupFeature.LARGE_FILES, listOf(file()))
        val smart =
            scan(
                index,
                CleanupFeature.SMART_CLEAN,
                listOf(
                    file().copy(bucket = "DUPLICATES", groupKey = "group", retained = true),
                    file(uri = "file:///fixture/copy.jpg", path = "/fixture/copy.jpg")
                        .copy(bucket = "DUPLICATES", groupKey = "group"),
                ),
            )
        index.selectAll(smart, CleanupFilter(), true)
        val op = index.prepareOperation(smart, CleanupFilter())
        index.remove(index.page(large, CleanupFilter(), 0, 60).single().id)
        val survivor = index.page(smart, CleanupFilter(), 0, 60).single()
        assertTrue(survivor.retained)
        assertFalse(survivor.selected)
        assertEquals(1, index.operationCount(op, "skipped"))
        assertEquals(0, JunkIndex(index).categories(smart.id).sumOf { it.count })
    }

    @Test
    fun platformMetricsSettleWithoutRequestingPermissions() = runBlocking {
        val result = HomePlatformMetricsSource(context, executor, (context.applicationContext as CleanApplication).container.installedAppCount).observe().toList().last()
        assertNotEquals(HomeToolMetric.Reading, result.network)
        assertNotEquals(HomeToolMetric.Reading, result.apps)
        if (result.network == HomeToolMetric.AccessRequired) assertNull(result.wifiBytes)
        result.wifiBytes?.let { assertTrue(it >= 0) }
        if (result.apps is HomeToolMetric.Bytes)
            assertTrue((result.apps as HomeToolMetric.Bytes).value >= 0)
        if (result.apps is HomeToolMetric.AppCount)
            assertTrue((result.apps as HomeToolMetric.AppCount).value > 0)
    }

    @Test
    fun debugPreviewIsOptInAndOldDefaultsDoNotOverrideRealMetrics() {
        assertNull(HomePreviewSupport.initialSelection(Intent(), null))
        assertNull(HomePreviewSupport.content(null))
        val old = Bundle().apply { putString(HomePreviewSupport.STATE_KEY, "initial") }
        assertNull(HomePreviewSupport.initialSelection(Intent(), old))
        assertEquals(
            "initial",
            HomePreviewSupport.initialSelection(Intent().putExtra("home_preview", "initial"), null),
        )
        val explicit = Bundle()
        HomePreviewSupport.saveSelection(explicit, "scanned")
        assertEquals("scanned", HomePreviewSupport.initialSelection(Intent(), explicit))
        assertNotNull(HomePreviewSupport.content("scanned"))
        HomePreviewSupport.saveSelection(explicit, null)
        assertNull(HomePreviewSupport.initialSelection(Intent(), explicit))
    }
}
