package com.example.aicleanphonestorage

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ScreenJunkCleaningBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep
import com.example.aicleanphonestorage.feature.home.data.StorageSummary
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import com.example.aicleanphonestorage.feature.junkcleaner.ui.JunkCategoriesAdapter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只使用本测试创建的缓存图片和独立数据库；无需解锁，不修改设备授权或用户照片。 */
@RunWith(AndroidJUnit4::class)
class JunkCleanerDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as CleanApplication).container

    private val index
        get() = container.fileScanRepository.index

    private fun root() = File(context.cacheDir, "junk_test_${UUID.randomUUID()}").apply { mkdirs() }

    private fun row(file: File, root: File) =
        ScannedFile(
            uri = Uri.fromFile(file).toString(),
            name = file.name,
            mime = if (file.extension == "png") "image/png" else "image/jpeg",
            size = file.length(),
            modifiedMillis = file.lastModified(),
            category = FileCategory.PHOTOS,
            backend = FileBackend.DIRECT,
            scope = root.canonicalPath,
            path = file.canonicalPath,
        )

    private fun picture(
        root: File,
        name: String,
        width: Int = 800,
        height: Int = 600,
        quality: Int = 95,
    ): File {
        val file = File(root, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels =
            IntArray(width * height) { i ->
                val value = ((i % width) * 150 / width + (i / width) * 80 / height)
                Color.rgb(value, value, (value + 10).coerceAtMost(255))
            }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        file.outputStream().use {
            bitmap.compress(
                if (name.endsWith("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                quality,
                it,
            )
        }
        bitmap.recycle()
        return file
    }

    private suspend fun scan(files: List<ScannedFile>): ScanHandle {
        val id = index.start(CleanupFeature.SMART_CLEAN)
        index.insert(id, files)
        // 与生产扫描顺序一致：先完成照片分类和参考图保护，再发布默认选择。
        JunkPhotoAnalyzer(context, JunkIndex(index)).analyze(id) {}
        return ScanHandle(id, CleanupFeature.SMART_CLEAN, files.size, "Test fixtures")
            .also(index::finishScan)
    }

    @Test
    fun duplicateSelectionProtectsReferenceAndDeletesOnlyConfirmedCopy() = runBlocking {
        val root = root()
        var handle: ScanHandle? = null
        try {
            val original = picture(root, "original.png")
            val copy = File(root, "copy.png")
            original.copyTo(copy)
            original.setLastModified(System.currentTimeMillis() - 10_000)
            handle = scan(listOf(row(original, root), row(copy, root)))
            val filter = CleanupFilter(bucket = JunkKind.DUPLICATES.name)
            val rows = index.page(handle, filter, 0, 60)
            assertEquals(2, rows.size)
            assertEquals(1, rows.count { it.retained })
            val keeper = rows.single { it.retained }
            assertFalse(rows.single { !it.retained }.selected)
            index.selectAll(handle, filter, true)
            index.select(keeper.id, true)
            assertFalse(index.get(keeper.id)!!.selected)
            assertEquals(1, index.totals(handle, filter).selectedCount)
            val op = index.prepareOperation(handle, filter)
            val result = container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(1, result.summary.deleted)
            assertTrue(File(keeper.path).exists())
            assertEquals(
                0,
                JunkIndex(index)
                    .categories(handle.id)
                    .single { it.kind == JunkKind.DUPLICATES }
                    .count,
            )
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun missingReferenceRejectsCandidateDeletion() = runBlocking {
        val root = root()
        var handle: ScanHandle? = null
        try {
            val a = picture(root, "a.png")
            val b = File(root, "b.png")
            a.copyTo(b)
            handle = scan(listOf(row(a, root), row(b, root)))
            val filter = CleanupFilter(bucket = JunkKind.DUPLICATES.name)
            val rows = index.page(handle, filter, 0, 60)
            val candidate = rows.single { !it.retained }
            index.selectAll(handle, filter, true)
            val op = index.prepareOperation(handle, filter)
            File(rows.single { it.retained }.path).delete()
            val result = container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(0, result.summary.deleted)
            assertEquals(1, result.summary.failed)
            assertTrue(File(candidate.path).exists())
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun similarAnalysisKeepsHigherResolutionAndDoesNotDoubleCountGroups() = runBlocking {
        val root = root()
        var handle: ScanHandle? = null
        try {
            val small = picture(root, "small.jpg", 800, 600, 85)
            small.setLastModified(System.currentTimeMillis() - 20_000)
            val big = picture(root, "big.jpg", 1600, 1200, 95)
            handle = scan(listOf(row(small, root), row(big, root)))
            val rows = index.page(handle, CleanupFilter(bucket = JunkKind.SIMILAR.name), 0, 60)
            assertEquals(2, rows.size)
            assertEquals(big.canonicalPath, rows.single { it.retained }.path)
            assertEquals(1, JunkIndex(index).categories(handle.id).sumOf { it.count })
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun sameSizeSameTimestampEditsInvalidateDuplicateProof() = runBlocking {
        val root = root()
        var handle: ScanHandle? = null
        try {
            val a = picture(root, "a.png")
            val b = File(root, "b.png")
            a.copyTo(b)
            handle = scan(listOf(row(a, root), row(b, root)))
            val filter = CleanupFilter(bucket = JunkKind.DUPLICATES.name)
            val rows = index.page(handle, filter, 0, 60)
            val keeper = rows.single { it.retained }
            val candidate = rows.single { !it.retained }
            val changed = File(candidate.path)
            val bytes = changed.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            changed.writeBytes(bytes)
            changed.setLastModified(candidate.modifiedMillis)
            index.selectAll(handle, filter, true)
            val op = index.prepareOperation(handle, filter)
            val result = container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
            assertEquals(0, result.summary.deleted)
            assertEquals(1, result.summary.failed)
            assertTrue(changed.exists())
            assertTrue(File(keeper.path).exists())
        } finally {
            handle?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    @Test
    fun schemaOneUpgradesWithoutLosingExistingFileSelection() {
        val folder = root()
        val isolated =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getDatabasePath(name: String) = File(folder, name)
            }
        val path = isolated.getDatabasePath("cleanup_index.db")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                "CREATE TABLE scans(id INTEGER PRIMARY KEY,feature TEXT,created INTEGER,count INTEGER,label TEXT,partial INTEGER,ready INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE files(id INTEGER PRIMARY KEY,scan INTEGER,uri TEXT,name TEXT,mime TEXT,size INTEGER,modified INTEGER,category TEXT,backend TEXT,scope TEXT,path TEXT,selected INTEGER,quality INTEGER)"
            )
            db.execSQL("INSERT INTO scans VALUES(1,'LARGE_FILES',1000,1,'test',0,1)")
            db.execSQL(
                "INSERT INTO files VALUES(1,1,'file:///test/a','a','text/plain',12000000,1000,'DOCUMENTS','DIRECT','/test','/test/a',1,75)"
            )
            db.version = 1
        }
        try {
            ScanIndex(isolated).use { database ->
                assertEquals(5, database.readableDatabase.version)
                assertEquals(CleanupFeature.LARGE_FILES, database.handle(1)!!.feature)
                val restored = database.get(1)!!
                assertTrue(restored.selected)
                assertFalse(restored.retained)
                assertEquals("", restored.bucket)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun overviewMeasuresLargeNumbersAndCanScrollInLandscape() {
        for ((widthDp, heightDp, scale) in
            listOf(Triple(375, 812, 1f), Triple(320, 640, 2f), Triple(640, 320, 1f))) {
            var output: Bitmap? = null
            var selectedOutput: Bitmap? = null
            instrumentation.runOnMainSync {
                val configuration =
                    Configuration(context.resources.configuration).apply { fontScale = scale }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(configuration),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val binding = ScreenJunkCleaningBinding.inflate(LayoutInflater.from(themed))
                val adapter = JunkCategoriesAdapter({}, { _, _ -> })
                binding.junkCategories.layoutManager = LinearLayoutManager(themed)
                binding.junkCategories.adapter = adapter
                val categories =
                    JunkKind.entries.mapIndexed { i, kind ->
                        JunkCategorySummary(kind, 20, if (kind == JunkKind.EMPTY_FOLDERS) 0 else 40_000_000L * (i + 1), 20)
                    }
                adapter.submit(
                    JunkSnapshot(
                        ScanHandle(1, CleanupFeature.SMART_CLEAN, 100, "Shared storage"),
                        categories,
                        StorageSummary(128_000_000_000, 89_700_000_000),
                    ),
                    true,
                )
                val density = themed.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, width, height)
                val bytes = binding.root.findViewById<TextView>(R.id.junk_bytes)
                val unit = binding.root.findViewById<TextView>(R.id.junk_unit)
                assertNotNull(bytes)
                assertTrue(bytes.right <= unit.left)
                assertTrue(
                    "Baseline rounding exceeds one pixel",
                    kotlin.math.abs(bytes.top + bytes.baseline - unit.top - unit.baseline) <= 1,
                )
                assertTrue(bytes.height >= bytes.layout.height)
                if (scale > 1f || heightDp < widthDp) assertTrue(binding.junkCategories.canScrollVertically(1))
                assertEquals(6, adapter.itemCount) // 头部、分区、四个可见分类；照片分类保留代码但隐藏。
                output =
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        binding.root.draw(Canvas(it))
                    }
                // 大字体/横屏首屏可能只有头部，先将分类滚入可见区域再检查真实复用行。
                (binding.junkCategories.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(2, 0)
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, width, height)
                val category = binding.junkCategories.findViewHolderForAdapterPosition(2)!!
                assertTrue(category.itemView.findViewById<View>(R.id.junk_category_check).isSelected)
                selectedOutput = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    binding.root.draw(Canvas(it))
                }
            }
            val folder =
                File(context.getExternalFilesDir(null), "junk-layout-tests").apply { mkdirs() }
            selectedOutput!!.let { image ->
                File(folder, "selected_${widthDp}_${heightDp}_${scale}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            output!!.let { image ->
                File(folder, "overview_${widthDp}_${heightDp}_${scale}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
        }
    }
}
