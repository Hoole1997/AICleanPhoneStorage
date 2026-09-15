package com.example.aicleanphonestorage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.CompressionQualityDialog
import com.example.aicleanphonestorage.feature.filecleaner.ui.FileCleanupActivity
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只创建/处理本测试专属文件，不授予权限、不删除用户内容；真实 SQLite/Paging/编码器均在设备执行。 */
@RunWith(AndroidJUnit4::class)
class CleanupFeatureDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as CleanApplication).container

    private val index
        get() = container.fileScanRepository.index

    private fun fixture(root: File, name: String, size: Long = 12_000_000): ScannedFile {
        val file = File(root, name)
        RandomAccessFile(file, "rw").use { it.setLength(size) }
        file.setLastModified(System.currentTimeMillis() - 100L * 86_400_000)
        return ScannedFile(
            uri = Uri.fromFile(file).toString(),
            name = name,
            mime = "application/pdf",
            size = file.length(),
            modifiedMillis = file.lastModified(),
            category = FileCategory.DOCUMENTS,
            backend = FileBackend.DIRECT,
            scope = root.canonicalPath,
            path = file.canonicalPath,
        )
    }

    private fun scan(feature: CleanupFeature, rows: List<ScannedFile>): ScanHandle {
        val id = index.start(feature)
        index.insert(id, rows)
        return ScanHandle(id, feature, rows.size, "Test fixtures").also(index::finishScan)
    }

    @Test
    fun pagingAndSelectionSnapshotStayIndependentFromLaterFilterChanges() {
        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        var handle: ScanHandle? = null
        try {
            val files = (0..124).map { fixture(root, "report_$it.pdf", 10_000_000 + it.toLong()) }
            handle = scan(CleanupFeature.LARGE_FILES, files)
            val filter = CleanupFilter()
            assertEquals(60, index.page(handle, filter, 0, 60).size)
            assertEquals(5, index.page(handle, filter, 120, 60).size)
            index.selectAll(handle, filter, true)
            val operation = index.prepareOperation(handle, filter)
            index.selectAll(handle, filter, false)
            assertEquals(125, index.operationCount(operation))
            assertEquals(0, index.totals(handle, filter).selectedCount)
            assertEquals(0, index.totals(handle, filter.copy(minimumBytes = 50_000_000)).count)
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun deletionRejectsStaleAndEscapingFilesAndOnlyDeletesSnapshot() = runBlocking {
        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        var handle: ScanHandle? = null
        try {
            val a = fixture(root, "valid.pdf")
            val b = fixture(root, "changed.pdf")
            handle = scan(CleanupFeature.LARGE_FILES, listOf(a, b))
            index.selectAll(handle, CleanupFilter(), true)
            val op = index.prepareOperation(handle, CleanupFilter())
            File(b.path).appendText("changed")
            val result = container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(1, result.summary.deleted)
            assertEquals(1, result.summary.failed)
            assertFalse(File(a.path).exists())
            assertTrue(File(b.path).exists())
            try {
                FileContentAccess(context).validate(b.copy(scope = File(root, "other").path))
                fail("Out-of-scope file accepted")
            } catch (_: IllegalArgumentException) {}
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun compressorCreatesVerifiedSmallerCopyAndPreservesOriginal() = runBlocking {
        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        var output: Uri? = null
        try {
            val source = photo(root, 0)
            val bytes = File(source.path).readBytes()
            val copy = PhotoCompressor(context, FileContentAccess(context)).compress(source)
            assertNotNull(copy)
            output = Uri.parse(copy!!.uri)
            assertTrue(copy.bytes < source.size)
            assertArrayEquals(bytes, File(source.path).readBytes())
            assertEquals(64, copy.sha256.length)
        } finally {
            output?.let { context.contentResolver.delete(it, null, null) }
            root.deleteRecursively()
        }
    }

    @Test
    fun fourPagesRenderSelectionConfirmationAndSurviveRecreation() {
        org.junit.Assume.assumeTrue(
            "UI tests require an unlocked, awake device",
            context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked,
        )

        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val photos = (0..11).map { photo(root, it) }
            for (feature in CleanupFeature.entries.filter { it != CleanupFeature.SMART_CLEAN }) {
                val handle =
                    scan(
                        feature,
                        if (
                            feature == CleanupFeature.LARGE_FILES ||
                                feature == CleanupFeature.UNUSED_FILES
                        )
                            (0..11).map { fixture(root, "document_${feature}_$it.pdf") }
                        else photos,
                    )
                try {
                    ActivityScenario.launch<FileCleanupActivity>(
                            Intent(context, FileCleanupActivity::class.java)
                                .putExtra(FileCleanupActivity.EXTRA_SCAN, handle.id)
                        )
                        .use { scenario ->
                            waitUntil {
                                var ready = false
                                scenario.onActivity {
                                    ready =
                                        it.findViewById<RecyclerView>(R.id.cleanup_files)
                                            .childCount > 0
                                }
                                ready
                            }
                            onView(withId(R.id.cleanup_action)).check(matches(isNotEnabled()))
                            screenshot("${feature}_unselected")
                            onView(withId(R.id.cleanup_select_all)).perform(click())
                            waitUntil {
                                var enabled = false
                                scenario.onActivity {
                                    enabled = it.findViewById<View>(R.id.cleanup_action).isEnabled
                                }
                                enabled
                            }
                            screenshot("${feature}_selected")
                            scenario.recreate()
                            waitUntil {
                                var ready = false
                                scenario.onActivity {
                                    ready = it.findViewById<View>(R.id.cleanup_action).isEnabled
                                }
                                ready
                            }
                            if (feature != CleanupFeature.PHOTO_COMPRESS) {
                                onView(withId(R.id.cleanup_action)).perform(click())
                                onView(withId(R.id.confirm_title)).check(matches(isDisplayed()))
                                screenshot("${feature}_confirm")
                                onView(withId(R.id.confirm_cancel)).perform(click())
                            }
                        }
                } finally {
                    index.discard(handle.id)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun customQualityDialogRestoresAndUpdatesOnlyItsPhoto() {
        val root = File(context.cacheDir, "quality_test_${UUID.randomUUID()}").apply { mkdirs() }
        val handle = scan(CleanupFeature.PHOTO_COMPRESS, listOf(photo(root, 1), photo(root, 2)))
        try {
            val rows = index.page(handle, CleanupFilter(), 0, 2)
            val target = rows.first()
            ActivityScenario.launch<FileCleanupActivity>(
                    Intent(context, FileCleanupActivity::class.java)
                        .putExtra(FileCleanupActivity.EXTRA_SCAN, handle.id)
                )
                .use { scenario ->
                    waitUntil {
                        var ready = false
                        scenario.onActivity {
                            ready =
                                it.lifecycle.currentState.isAtLeast(
                                    androidx.lifecycle.Lifecycle.State.RESUMED
                                ) &&
                                    it.hasWindowFocus() &&
                                    it.findViewById<RecyclerView>(R.id.cleanup_files)
                                        .findViewHolderForAdapterPosition(0) != null
                        }
                        ready
                    }
                    fun open() =
                        scenario.onActivity {
                            it.findViewById<RecyclerView>(R.id.cleanup_files)
                                .findViewHolderForAdapterPosition(0)!!
                                .itemView
                                .findViewById<View>(R.id.photo_saving_action)
                                .performClick()
                            assertNotNull(
                                it.supportFragmentManager.findFragmentByTag(
                                    CompressionQualityDialog.TAG
                                )
                            )
                        }
                    open()
                    screenshot("quality_actual")
                    onView(withId(R.id.quality_balanced))
                        .inRoot(isDialog())
                        .check(matches(isChecked()))
                    scenario.recreate()
                    onView(withId(R.id.quality_balanced))
                        .inRoot(isDialog())
                        .check(matches(isChecked()))
                    assertEquals(75, index.get(target.id)!!.quality)
                    onView(withId(R.id.quality_high)).inRoot(isDialog()).perform(click())
                    waitUntil { index.get(target.id)!!.quality == 85 }
                    assertEquals(75, index.get(rows.last().id)!!.quality)
                    assertEquals(0, index.totals(handle, CleanupFilter()).selectedCount)
                    waitUntil {
                        var updated = false
                        scenario.onActivity {
                            updated =
                                it.findViewById<RecyclerView>(R.id.cleanup_files)
                                    .findViewHolderForAdapterPosition(0)
                                    ?.itemView
                                    ?.findViewById<View>(R.id.photo_saving_action)
                                    ?.contentDescription
                                    ?.endsWith("85") == true
                        }
                        updated
                    }
                    open()
                    onView(withId(R.id.quality_high)).inRoot(isDialog()).check(matches(isChecked()))
                    screenshot("quality_custom")
                    onView(withId(R.id.quality_cancel)).inRoot(isDialog()).perform(click())
                    assertEquals(85, index.get(target.id)!!.quality)
                    scenario.onActivity {
                        assertNull(
                            it.supportFragmentManager.findFragmentByTag(
                                CompressionQualityDialog.TAG
                            )
                        )
                    }
                }
        } finally {
            index.discard(handle.id)
            root.deleteRecursively()
        }
    }

    @Test
    fun directScanSkipsProtectedDirectoriesAndCancelsCooperatively() = runBlocking {
        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        val handle = scan(CleanupFeature.LARGE_FILES, emptyList())
        try {
            fixture(root, "visible.pdf")
            val protected = File(root, "Android/data").apply { mkdirs() }
            fixture(protected, "private.pdf")
            val found = mutableListOf<ScannedFile>()
            val sources =
                com.example.aicleanphonestorage.feature.filecleaner.scan.FileScanSources(
                    context,
                    index,
                )
            val access =
                com.example.aicleanphonestorage.feature.filecleaner.scan.ScanAccess(
                    com.example.aicleanphonestorage.feature.filecleaner.scan.AccessRequest.NONE,
                    com.example.aicleanphonestorage.feature.filecleaner.scan.ScanSourceKind.DIRECT,
                    listOf(root.canonicalPath),
                )
            sources.scan(access, handle.id, { file, _ -> found += file }) { _, _ -> }
            assertEquals(listOf("visible.pdf"), found.map { it.name })
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Job().apply { cancel() }) {
                    sources.scan(
                        access,
                        handle.id,
                        { _, _ -> fail("Cancelled scan emitted a file") },
                    ) { _, _ ->
                    }
                }
                fail("Scan ignored cancellation")
            } catch (_: kotlinx.coroutines.CancellationException) {}
        } finally {
            index.discard(handle.id)
            root.deleteRecursively()
        }
    }

    @Test
    fun missingCompressedCopyPreventsOriginalDeletion() = runBlocking {
        val root = File(context.cacheDir, "cleanup_test_${UUID.randomUUID()}").apply { mkdirs() }
        var handle: ScanHandle? = null
        var output: Uri? = null
        try {
            val original = photo(root, 3)
            handle = scan(CleanupFeature.PHOTO_COMPRESS, listOf(original))
            index.selectAll(handle, CleanupFilter(), true)
            val op = index.prepareOperation(handle, CleanupFilter())
            val result = container.fileOperations.compress(op) { _, _ -> } as OperationStep.Finished
            assertEquals(1, result.summary.copied)
            val row = index.operationFiles(op, "copied").single()
            output = Uri.parse(index.output(op, row.id)!!.first)
            context.contentResolver.delete(output, null, null)
            output = null // 已移除的 URI 再次 delete 在部分 OEM 上会返回 SecurityException。
            container.fileOperations.deleteOriginals(op)
            val deletion = container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(0, deletion.summary.deleted)
            assertEquals(1, deletion.summary.failed)
            assertTrue(File(original.path).exists())
        } finally {
            output?.let { context.contentResolver.delete(it, null, null) }
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    private fun photo(root: File, number: Int): ScannedFile {
        val file = File(root, "Screenshot_fixture_$number.png")
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(number.toLong())
        val pixels =
            IntArray(600 * 800) {
                Color.rgb(
                    random.nextInt(80) + number * 10,
                    random.nextInt(150) + 50,
                    random.nextInt(80) + 100,
                )
            }
        bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 800)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return ScannedFile(
            uri = Uri.fromFile(file).toString(),
            name = file.name,
            mime = "image/png",
            size = file.length(),
            modifiedMillis = file.lastModified(),
            category = FileCategory.PHOTOS,
            backend = FileBackend.DIRECT,
            scope = root.canonicalPath,
            path = file.canonicalPath,
        )
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(300)
        val folder =
            File(context.getExternalFilesDir(null), "cleanup-test-screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
        assertTrue("UI did not settle", condition())
    }
}
