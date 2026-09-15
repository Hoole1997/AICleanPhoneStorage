package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 独立索引验证统计口径；展示用样例与实际设备照片隔离。 */
@RunWith(AndroidJUnit4::class)
class CompressionSizeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun selectedSizeHeaderUsesTheSameDisplayAsCompletion() {
        for (locale in listOf(Locale.US, Locale.SIMPLIFIED_CHINESE, Locale.GERMAN))
        for (widthDp in listOf(320, 375))
        for (scale in listOf(1f, 2f)) instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale); fontScale = scale
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val binding = com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed))
            binding.cleanupTitle.setText(R.string.cleanup_photo)
            val renderer = com.example.aicleanphonestorage.feature.filecleaner.ui.CleanupListStateRenderer(binding)
            val state = com.example.aicleanphonestorage.feature.filecleaner.ui.CleanupUiState(
                handle = ScanHandle(1, CleanupFeature.PHOTO_COMPRESS, 10, "Test fixtures"),
                totals = SelectionTotals(count = 10, bytes = 55_800_000, selectedCount = 5, selectedBytes = 27_900_000),
                totalsReady = true,
            )
            renderer.state(state)
            assertEquals(if (locale == Locale.GERMAN) "27,9 MB" else "27.9 MB", binding.cleanupPotential.text.toString())
            val density = themed.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (740 * density).toInt()
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            assertTrue(binding.cleanupPotential.height >= binding.cleanupPotential.layout.height)
            // 长文案允许自然换行，但不能裁切或与右侧容量徽标重叠。
            val label = binding.cleanupPhotoHeader.getChildAt(0) as android.widget.TextView
            assertTrue(label.height - label.compoundPaddingTop - label.compoundPaddingBottom >= label.layout.height)
            assertTrue(label.right <= binding.cleanupPotential.left)
            val image = Bitmap.createBitmap(width, binding.cleanupPhotoHeader.bottom + (8 * density).toInt(), Bitmap.Config.ARGB_8888)
            try {
                binding.root.draw(Canvas(image))
                val folder = File(context.getExternalFilesDir(null), "compression-size-tests").apply { mkdirs() }
                File(folder, "selected_header_${locale.language}_${widthDp}_$scale.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { image.recycle() }
            renderer.state(state.copy(totals = state.totals.copy(selectedCount = 0, selectedBytes = 0)))
            assertEquals(if (locale == Locale.GERMAN) "0,0 MB" else "0.0 MB", binding.cleanupPotential.text.toString())
        }
    }

    @Test fun partialCancelledAndEmptyOperationsKeepSelectedAndSuccessfulSizesSeparate() {
        val folder = File(context.cacheDir, "compression_sizes_${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(folder, name)
        }
        try {
            ScanIndex(isolated).use { index ->
                val scan = index.start(CleanupFeature.PHOTO_COMPRESS)
                index.insert(scan, listOf(2_000L, 3_000L, 5_000L).mapIndexed { i, size -> ScannedFile(
                    uri = "file:///size-fixtures/$i", name = "photo_$i", mime = "image/png", size = size,
                    modifiedMillis = 1, category = FileCategory.PHOTOS, backend = FileBackend.DIRECT, scope = "/size-fixtures",
                ) })
                val handle = ScanHandle(scan, CleanupFeature.PHOTO_COMPRESS, 3, "Test fixtures").also(index::finishScan)
                index.selectAll(handle, CleanupFilter(), true)
                val input = index.totals(handle, CleanupFilter()).selectedBytes
                assertEquals(10_000L, input)
                val rows = index.page(handle, CleanupFilter(), 0, 60).sortedBy { it.size }
                index.quality(rows.first().id, 60)
                assertEquals(input, index.totals(handle, CleanupFilter()).selectedBytes)
                val op = index.prepareOperation(handle, CleanupFilter())
                index.mark(op, rows[0].id, "copied", "content://fixture/copy", 500, "fixture")
                index.mark(op, rows[1].id, "failed")
                index.mark(op, rows[2].id, "skipped")
                index.selectAll(handle, CleanupFilter(), false)
                val storage = index.operationStorage(op)
                assertEquals(input, storage.inputBytes)
                assertEquals(2_000L, storage.copiedOriginalBytes)
                assertEquals(500L, storage.outputBytes)
                assertEquals(1_500L, storage.reducedBytes)
                assertEquals(0L, storage.freedBytes) // 原图仍在，创建副本并没有释放磁盘空间。
                index.selectAll(handle, CleanupFilter(), true)
                val cancelled = index.prepareOperation(handle, CleanupFilter())
                index.cancelPending(cancelled)
                assertEquals(OperationStorage(0, 0, input, 0, 0), index.operationStorage(cancelled))
                index.selectAll(handle, CleanupFilter(), false)
                assertEquals(OperationStorage(0, 0, 0, 0, 0), index.operationStorage(index.prepareOperation(handle, CleanupFilter())))
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun resultDisplaysComparableSizesAndPreservesThemAcrossIntentRestore() {
        val reports = listOf(
            CompletionReport(CompletionKind.COMPRESSION, 3, reducedBytes = 17_700_000, originalsRemaining = 3,
                inputBytes = 27_900_000, copiedOriginalBytes = 27_900_000, outputBytes = 10_200_000),
            CompletionReport(CompletionKind.COMPRESSION, 1, failed = 1, skipped = 1, reducedBytes = 9_800_000, originalsRemaining = 1,
                inputBytes = 27_900_000, copiedOriginalBytes = 20_000_000, outputBytes = 10_200_000),
            CompletionReport(CompletionKind.COMPRESSION, 0, failed = 3,
                inputBytes = 27_900_000, copiedOriginalBytes = 0, outputBytes = 0),
        )
        reports.forEachIndexed { index, report ->
            assertEquals(report, CompletionContract.read(CompletionContract.intent(context, report)))
            for (scale in listOf(1f, 2f)) instrumentation.runOnMainSync {
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.SIMPLIFIED_CHINESE); fontScale = scale
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
                val binding = ScreenCompletionBinding.inflate(LayoutInflater.from(themed))
                CompletionRenderer(binding).render(report)
                val detail = binding.completionDetail.text.toString()
                assertTrue(detail.contains("已选原图：27.9 MB"))
                if (report.completed > 0) assertTrue(detail.contains("压缩后副本：10.2 MB"))
                if (report.partial) assertTrue(detail.contains("成功压缩的原图：20.0 MB"))
                val density = themed.resources.displayMetrics.density
                val width = (375 * density).toInt()
                val height = (740 * density).toInt()
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, width, height)
                assertTrue(binding.completionDetail.height >= binding.completionDetail.layout.height)
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    binding.root.draw(Canvas(image))
                    val folder = File(context.getExternalFilesDir(null), "compression-size-tests").apply { mkdirs() }
                    File(folder, "result_${index}_$scale.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { image.recycle() }
            }
        }
        val legacy = CompletionReport(CompletionKind.COMPRESSION, 1, reducedBytes = 500)
        assertNull(CompletionContract.read(CompletionContract.intent(context, legacy))!!.inputBytes)
    }
}
