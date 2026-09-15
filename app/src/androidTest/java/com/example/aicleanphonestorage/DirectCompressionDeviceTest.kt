package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用缓存测试图片，验证无确认弹框的压缩入口和真实副本大小。 */
@RunWith(AndroidJUnit4::class)
class DirectCompressionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun compressionSkipsConfirmationAndDuplicateStartCannotRepeatWork() {
        val container = (context.applicationContext as CleanApplication).container
        val index = container.fileScanRepository.index
        val folder = File(context.cacheDir, "direct_compress_${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(folder, "photo.png")
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        bitmap.setPixels(IntArray(600 * 600) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }, 0, 600, 0, 0, 600, 600)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = source.readBytes()
        val scan = index.start(CleanupFeature.PHOTO_COMPRESS)
        val store = ViewModelStore()
        var output: Uri? = null
        try {
            index.insert(scan, listOf(ScannedFile(
                uri = Uri.fromFile(source).toString(), name = source.name, mime = "image/png", size = source.length(),
                modifiedMillis = source.lastModified(), category = FileCategory.PHOTOS, backend = FileBackend.DIRECT,
                scope = folder.canonicalPath, path = source.canonicalPath,
            )))
            val handle = ScanHandle(scan, CleanupFeature.PHOTO_COMPRESS, 1, "Test fixtures").also(index::finishScan)
            index.selectAll(handle, CleanupFilter(), true)
            // 将体积验证直接连接到广告结束后的业务入口，不依赖外部广告或 Activity 恢复时机。
            lateinit var model: CleanupViewModel
            instrumentation.runOnMainSync {
                model = CleanupViewModel(container.fileScanRepository, container.fileOperations, SavedStateHandle(), scan)
                store.put("compress", model)
            }
            waitUntil { model.state.value.totalsReady }
            val selectedBytes = model.state.value.totals.selectedBytes
            instrumentation.runOnMainSync { model.prepare() }
            waitUntil { model.state.value.operation is CleanupOperationState.CompressionReady }
            val ready = model.state.value.operation as CleanupOperationState.CompressionReady
            instrumentation.runOnMainSync {
                model.startCompression(ready.id + 1)
                assertEquals(ready, model.state.value.operation)
                model.startCompression(ready.id)
                model.startCompression(ready.id) // 重复回调不能再创建一份压缩任务。
            }
            waitUntil { model.state.value.operation is CleanupOperationState.Result }
            val result = model.state.value.operation as CleanupOperationState.Result
            assertEquals(1, result.summary.copied)
            assertEquals(0, result.summary.deleted)
            assertEquals(0, result.summary.failed)
            val copied = index.operationFiles(result.id, "copied").single()
            output = Uri.parse(index.output(result.id, copied.id)!!.first)
            val actualOutputBytes = context.contentResolver.openInputStream(output!!).use {
                assertNotNull(it)
                it!!.readBytes().size.toLong()
            }
            assertEquals(selectedBytes, result.summary.inputBytes)
            assertEquals(source.length(), result.summary.copiedOriginalBytes)
            assertEquals(actualOutputBytes, result.summary.outputBytes)
            assertEquals(source.length() - actualOutputBytes, result.summary.reducedBytes)
            val report = result.summary.completionReport(CleanupFeature.PHOTO_COMPRESS, result.id)
            val contract = com.example.aicleanphonestorage.core.ui.completion.CompletionContract
            assertEquals(report, contract.read(contract.intent(context, report)))
            assertArrayEquals(original, source.readBytes())
            instrumentation.runOnMainSync {
                model.startCompression(ready.id)
                assertEquals(result, model.state.value.operation)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            output?.let { context.contentResolver.delete(it, null, null) }
            index.discard(scan)
            folder.deleteRecursively()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
        assertTrue("Compression did not reach the expected state", condition())
    }
}
