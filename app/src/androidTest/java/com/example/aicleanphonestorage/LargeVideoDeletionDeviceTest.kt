package com.example.aicleanphonestorage

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.DirectStorageScanner
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真机真实 MediaStore/文件系统。只生成和处理此测试持有的 URI，绝不选择已有用户文件。 */
@RunWith(AndroidJUnit4::class)
class LargeVideoDeletionDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val resolver get() = context.contentResolver
    private val executor = TaskExecutor(AppDispatchers())
    private val index get() = (context.applicationContext as CleanApplication).container.fileScanRepository.index
    private val fixtures = mutableListOf<Uri>()
    private val scans = mutableListOf<Long>()

    private fun video(folder: String, name: String = "same-name.mp4"): Pair<Uri, ScannedFile> {
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put("_display_name", name); put("mime_type", "video/mp4")
            put("relative_path", "Movies/AICleanLargeVideoTest/$folder"); put("is_pending", 1)
        })!!
        fixtures += uri
        resolver.openFileDescriptor(uri, "rw")!!.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                instrumentation.context.assets.open("video-fixture.mp4").use { it.copyTo(output) }
                // 有效视频后追加稀疏尾部，使其达到大文件门槛；不分配大字节数组。
                output.channel.position(10_100_000 - 1); output.write(0)
            }
        }
        resolver.update(uri, ContentValues().apply { put("is_pending", 0) }, null, null)
        val path = resolver.query(uri, arrayOf("_data"), null, null, null)!!.use { assertTrue(it.moveToFirst()); it.getString(0) }
        val file = File(path)
        val rows = mutableListOf<ScannedFile>()
        runBlocking { executor.io {
            DirectStorageScanner { "video/mp4" }.scan(listOf(file.parent!!), false,
                { row, _ -> if (row.path == path) rows += row }, { _, _ -> })
        } }
        assertEquals(1, rows.size)
        return uri to rows.single()
    }

    private fun prepare(rows: List<ScannedFile>, selected: Set<String> = rows.map { it.uri }.toSet()): Pair<ScanHandle, Long> {
        val id = index.start(CleanupFeature.LARGE_FILES); scans += id
        index.insert(id, rows)
        val handle = ScanHandle(id, CleanupFeature.LARGE_FILES, rows.size, "Test fixtures")
        index.finishScan(handle)
        index.page(handle, CleanupFilter(), 0, 100).forEach { index.select(it.id, it.uri in selected) }
        return handle to index.prepareOperation(handle, CleanupFilter())
    }

    private fun exists(uri: Uri) = resolver.query(uri, arrayOf("_id"), null, null, null)!!.use { it.moveToFirst() }
    private fun cleanup() {
        fixtures.forEach { resolver.delete(it, null, null) }
        scans.forEach(index::discard)
    }

    @Test fun selectedDirectVideoUsesMediaStoreAndSameNamedUnselectedVideoSurvives() = runBlocking {
        assumeTrue(Environment.isExternalStorageManager())
        try {
            val prefix = System.nanoTime().toString()
            val (uri, row) = video("$prefix/a")
            val (untouched, other) = video("$prefix/b")
            val (_, op) = prepare(listOf(row, other), setOf(row.uri))
            val calls = mutableListOf<Uri>()
            val engine = FileOperationEngine(context, index, executor, deleteMedia = {
                calls += it; resolver.delete(it, null, null)
            })
            val result = engine.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(1, result.summary.deleted); assertEquals(0, result.summary.failed)
            assertEquals(1, calls.size)
            assertEquals(uri.lastPathSegment, calls.single().lastPathSegment)
            assertTrue(calls.single().path!!.contains("/video/media/"))
            assertFalse(exists(uri)); assertFalse(File(row.path).exists())
            assertTrue(exists(untouched)); assertTrue(File(other.path).exists())
        } finally { cleanup() }
    }

    @Test fun changedSourceAndStaleMediaMetadataAreRejectedBeforeDelete() = runBlocking {
        assumeTrue(Environment.isExternalStorageManager())
        try {
            val prefix = System.nanoTime().toString()
            val (uri, row) = video("$prefix/source")
            val (_, op) = prepare(listOf(row))
            FileOutputStream(File(row.path), true).use { it.write(1) }
            val engine = FileOperationEngine(context, index, executor, deleteMedia = { fail("Changed source must not be deleted"); 0 })
            val result = engine.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(1, result.summary.failed); assertEquals(0, result.summary.deleted); assertTrue(exists(uri))

            val (staleUri, stale) = video("$prefix/media")
            // 只改文件系统时间，不同步媒体库：源快照有效，但映射的媒体记录已不一致。
            val physical = File(stale.path)
            assertTrue(physical.setLastModified(physical.lastModified() - 60_000))
            val (_, staleOp) = prepare(listOf(stale.copy(modifiedMillis = physical.lastModified())))
            val staleResult = engine.delete(staleOp) { _, _ -> } as OperationStep.Finished
            assertEquals(1, staleResult.summary.failed); assertTrue(exists(staleUri))
        } finally { cleanup() }
    }

    @Test fun providerDenialUsesSystemConsentAndVerifiesPersistedMediaUri() = runBlocking {
        assumeTrue(Environment.isExternalStorageManager())
        try {
            val (uri, row) = video("${System.nanoTime()}/consent")
            val (handle, op) = prepare(listOf(row))
            val engine = FileOperationEngine(context, index, executor, deleteMedia = { throw SecurityException("Test provider denial") })
            assertTrue(engine.delete(op) { _, _ -> } is OperationStep.Consent)
            val source = index.operationFiles(op, "awaiting").single()
            assertEquals(FileBackend.DIRECT, source.backend)
            val target = Uri.parse(index.deleteUri(op, source.id)!!)
            assertEquals(uri.lastPathSegment, target.lastPathSegment)
            assertTrue(target.path!!.contains("/video/media/"))
            engine.consentResult(op, false)
            assertTrue(exists(uri)); assertEquals(0, engine.summary(op).deleted)

            val falselyAccepted = index.prepareOperation(handle, CleanupFilter())
            assertTrue(engine.delete(falselyAccepted) { _, _ -> } is OperationStep.Consent)
            engine.consentResult(falselyAccepted, true)
            assertEquals(1, engine.summary(falselyAccepted).failed); assertTrue(exists(uri))

            val accepted = index.prepareOperation(handle, CleanupFilter())
            assertTrue(engine.delete(accepted) { _, _ -> } is OperationStep.Consent)
            // 独立连接读取持久目标，模拟页面/进程重建后再处理系统结果。
            ScanIndex(context).use { restored ->
                assertEquals(target.toString(), restored.deleteUri(accepted, source.id))
                resolver.delete(uri, null, null) // 仅删除这个测试 URI，模拟系统确认完成。
                FileOperationEngine(context, restored, executor).consentResult(accepted, true)
                assertEquals(1, FileOperationEngine(context, restored, executor).summary(accepted).deleted)
            }
            assertFalse(File(row.path).exists())
        } finally { cleanup() }
    }

    @Test fun largeVideoStillRequiresInAppConfirmationAndCancelKeepsFile() {
        assumeTrue(Environment.isExternalStorageManager())
        try {
            val (uri, row) = video("${System.nanoTime()}/confirmation")
            val (handle, _) = prepare(listOf(row))
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, handle.id)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.LARGE_FILES.name)).use { scenario ->
                fun model(a: FileCleanupActivity) = ViewModelProvider(a)[CleanupViewModel::class.java]
                waitUntil { var ready = false; scenario.onActivity { ready = model(it).state.value.totals.selectedCount == 1 }; ready }
                scenario.onActivity { model(it).prepare() }
                waitUntil { var ready = false; scenario.onActivity { ready = model(it).state.value.operation is CleanupOperationState.Confirm }; ready }
                waitUntil {
                    var showing = false
                    scenario.onActivity { showing = (it.supportFragmentManager.findFragmentByTag(CleanupMessageDialog.TAG) as? CleanupMessageDialog)?.dialog?.isShowing == true }
                    showing
                }
                onView(withId(R.id.confirm_cancel)).inRoot(isDialog()).check(matches(isDisplayed()))
                instrumentation.waitForIdleSync()
                // 等待系统合成确认窗口首帧，再保存真实屏幕（不改生产动画/逻辑）。
                SystemClock.sleep(250)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try {
                        val dir = File(context.getExternalFilesDir(null), "large-video-delete-tests").apply { mkdirs() }
                        File(dir, "confirmation.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
                assertTrue(exists(uri))
                onView(withId(R.id.confirm_cancel)).inRoot(isDialog()).perform(click())
                waitUntil { var idle = false; scenario.onActivity { idle = model(it).state.value.operation == CleanupOperationState.Idle }; idle }
                assertTrue(exists(uri)); assertTrue(File(row.path).exists())
            }
        } finally { cleanup() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40)
        assertTrue("Cleanup state did not converge", condition())
    }
}
