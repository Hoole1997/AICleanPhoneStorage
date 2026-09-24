package com.example.aicleanphonestorage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.media.PhotoSignature
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationStep
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.similar.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只使用测试创建的图片与独立扫描会话；不查询/删除用户图库。 */
@RunWith(AndroidJUnit4::class)
class SimilarCleanerDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as CleanApplication).container

    private val repo
        get() = container.fileScanRepository

    private val index
        get() = repo.index

    private var transition: AutoCloseable? = null
    @org.junit.Before fun isolateEntryTransitions() {
        instrumentation.runOnMainSync {
            transition = com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard.hold("similar-ui-test")
        }
    }
    @org.junit.After fun releaseEntryTransitions() {
        instrumentation.runOnMainSync { transition?.close(); transition = null }
    }

    private fun root() = File(context.cacheDir, "similar-${System.nanoTime()}").apply { mkdirs() }

    private fun picture(root: File, name: String, tint: Int = 0, quality: Int = 95): File {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val pixels =
            IntArray(400 * 300) { i ->
                val shade = (i % 400) * 150 / 400 + (i / 400) * 80 / 300
                Color.rgb(shade, (shade + tint).coerceIn(0, 255), (shade + 10).coerceIn(0, 255))
            }
        bitmap.setPixels(pixels, 0, 400, 0, 0, 400, 300)
        val file = File(root, name)
        try {
            file.outputStream().use {
                bitmap.compress(
                    if (name.endsWith("png")) Bitmap.CompressFormat.PNG
                    else Bitmap.CompressFormat.JPEG,
                    quality,
                    it,
                )
            }
        } finally {
            bitmap.recycle()
        }
        file.setLastModified(1_780_000_000_000)
        return file
    }

    private fun file(f: File, root: File) =
        ScannedFile(
            uri = Uri.fromFile(f).toString(),
            name = f.name,
            mime = if (f.extension == "png") "image/png" else "image/jpeg",
            size = f.length(),
            modifiedMillis = f.lastModified(),
            category = FileCategory.PHOTOS,
            backend = FileBackend.DIRECT,
            scope = root.canonicalPath,
            path = f.canonicalPath,
            width = 400,
            height = 300,
        )

    private suspend fun scan(files: List<ScannedFile>): ScanHandle {
        val id = index.start(CleanupFeature.SIMILAR_PHOTOS)
        try {
            index.insert(id, files)
            val skipped = SimilarPhotoAnalyzer(context, repo.similar).analyze(id) {}
            return ScanHandle(
                    id,
                    CleanupFeature.SIMILAR_PHOTOS,
                    files.size,
                    "Test fixtures",
                    analysisSkipped = skipped,
                )
                .also(index::finishScan)
        } catch (e: Exception) {
            index.discard(id)
            throw e
        }
    }

    @Test
    fun exactAndVisualGroupsPreserveOriginalAndRejectChangedContent() =
        runBlocking<Unit> {
            val dir = root()
            var handle: ScanHandle? = null
            try {
                val a = picture(dir, "original.png")
                val b = File(dir, "copy.png")
                a.copyTo(b)
                b.setLastModified(a.lastModified() + 1000)
                val c = picture(dir, "similar-high.jpg", 50, 95)
                val d = picture(dir, "similar-low.jpg", 50, 80)
                handle = scan(listOf(a, b, c, d).map { file(it, dir) })
                val rows = index.page(handle, CleanupFilter(), 0, 20)
                assertEquals(4, rows.size)
                assertEquals(2, rows.map { it.groupKey }.distinct().size)
                assertEquals(2, rows.count { it.retained })
                assertEquals(2, index.totals(handle, CleanupFilter()).selectedCount)
                val exact = rows.filter { it.groupKey.startsWith("exact:") }
                assertEquals(2, exact.size)
                val old = exact.single { it.retained }
                val replacement = exact.single { !it.retained }
                index.select(old.id, true)
                assertFalse(index.get(old.id)!!.selected)
                repo.similar.makeOriginal(handle.id, replacement.id)
                assertTrue(index.get(replacement.id)!!.retained)
                assertFalse(index.get(replacement.id)!!.selected)
                index.selectAll(handle, CleanupFilter(), true)
                assertEquals(2, index.totals(handle, CleanupFilter()).selectedCount)
                val op = index.prepareOperation(handle, CleanupFilter(bucket = old.groupKey))
                assertEquals(listOf(old.id), index.operationFiles(op).map { it.id })
                val oldFile = File(old.path)
                val changed = oldFile.readBytes()
                changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
                oldFile.writeBytes(changed)
                oldFile.setLastModified(old.modifiedMillis)
                val result =
                    container.fileOperations.delete(op) { _, _ -> } as OperationStep.Finished
                assertEquals(0, result.summary.deleted)
                assertEquals(1, result.summary.failed)
                assertTrue(oldFile.exists())
                assertTrue(File(replacement.path).exists())
            } finally {
                handle?.let { index.discard(it.id) }
                dir.deleteRecursively()
            }
        }

    @Test fun exposureAndOldCopiesAreGroupedWithoutWeakeningOriginalProtection() = runBlocking<Unit> {
        val dir = root()
        var handle: ScanHandle? = null
        var mediaHandle: ScanHandle? = null
        val created = mutableListOf<Uri>()
        try {
            val original = picture(dir, "original.jpg")
            val bitmap = android.graphics.BitmapFactory.decodeFile(original.path)
            val values = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(values, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (i in values.indices) values[i] = Color.rgb(
                (Color.red(values[i]) + 30).coerceAtMost(255),
                (Color.green(values[i]) + 30).coerceAtMost(255),
                (Color.blue(values[i]) + 30).coerceAtMost(255))
            val edited = Bitmap.createBitmap(values, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val crop = Bitmap.createBitmap(edited, 8, 6, 384, 288)
            val resized = Bitmap.createScaledBitmap(crop, 240, 180, true)
            val copy = File(dir, "saved-later.jpg")
            try { copy.outputStream().use { resized.compress(Bitmap.CompressFormat.JPEG, 60, it) } }
            finally { resized.recycle(); crop.recycle(); edited.recycle(); bitmap.recycle() }
            copy.setLastModified(original.lastModified() + 45L * 86_400_000)
            handle = scan(listOf(file(original, dir), file(copy, dir).copy(width=240,height=180)))
            val rows = index.page(handle, CleanupFilter(), 0, 20)
            assertEquals(2, rows.size)
            assertEquals(1, rows.map { it.groupKey }.distinct().size)
            assertTrue(rows.first().groupKey.startsWith("similar:"))
            assertEquals(1, rows.count { it.retained })
            assertEquals(1, index.totals(handle, CleanupFilter()).selectedCount)
            assertTrue(original.exists()); assertTrue(copy.exists())
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                // 在同一设备上复核真实 MediaStore URI；只发布/移除本测试创建的两张图片。
                val media = listOf(original,copy).map { image ->
                    val resolver = context.contentResolver
                    val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        android.content.ContentValues().apply {
                            put("_display_name", "similar-visual-${System.nanoTime()}.jpg")
                            put("mime_type", "image/jpeg")
                            put("relative_path", "Pictures/SimilarCleanerTests")
                            put("datetaken", image.lastModified())
                            put("is_pending", 1)
                        }))
                    created += uri
                    resolver.openOutputStream(uri)!!.use { output -> image.inputStream().use { it.copyTo(output) } }
                    resolver.update(uri, android.content.ContentValues().apply { put("is_pending", 0) }, null, null)
                    resolver.query(uri,arrayOf("_display_name","_size","date_modified","datetaken"),null,null,null)!!.use { c ->
                        assertTrue(c.moveToFirst())
                        ScannedFile(uri=uri.toString(), name=c.getString(0), mime="image/jpeg", size=c.getLong(1),
                            modifiedMillis=c.getLong(2)*1000, takenMillis=c.getLong(3), category=FileCategory.PHOTOS,
                            backend=FileBackend.MEDIA, scope="external")
                    }
                }
                assertTrue(media.all { SimilarPolicy.candidate(it,"Pictures/SimilarCleanerTests/") })
                mediaHandle = scan(media)
                val mediaRows = index.page(mediaHandle, CleanupFilter(),0,10)
                assertEquals(2,mediaRows.size)
                assertEquals(1,mediaRows.map { it.groupKey }.distinct().size)
                assertEquals(1,mediaRows.count { it.retained })
            }
        } finally {
            handle?.let { index.discard(it.id) }; mediaHandle?.let { index.discard(it.id) }
            created.forEach { context.contentResolver.delete(it,null,null) }
            dir.deleteRecursively()
        }
    }

    @Test fun indexRecallIgnoresDateAndSizeAndUsesAllHashProbes() {
        val scan = index.start(CleanupFeature.SIMILAR_PHOTOS)
        try {
            val a = ScannedFile(uri="content://similar-fixture/a", name="a.jpg", mime="image/jpeg", size=100_000,
                modifiedMillis=1_000, category=FileCategory.PHOTOS, backend=FileBackend.MEDIA, scope="test")
            val b = a.copy(uri="content://similar-fixture/b", name="b.jpg", size=1_000,
                modifiedMillis=1_000 + 100L*86_400_000)
            index.insert(scan, listOf(a,b))
            val files = repo.similar.candidates(scan, 0)
            val hash = 0x123456789abcdefL
            val changed = hash xor (1L shl 1) xor (1L shl 14) xor (1L shl 27) xor (1L shl 40) xor (1L shl 53)
            val signature = PhotoSignature(hash, 1.33, 100, 30.0, 20.0, 10000,
                layout=ByteArray(64) { (40+it*2).toByte() })
            repo.similar.signature(files[0], signature)
            repo.similar.signature(files[1], signature.copy(hash=changed))
            val references = repo.similar.references(scan, files[1], signature.copy(hash=changed), files[1].id)
            assertEquals(listOf(files[0].id), references.map { it.first.id })
            assertTrue(SimilarPolicy.matches(signature.copy(hash=changed), references.single().second))
            assertTrue(repo.similar.references(scan, files[1], signature.copy(hash=changed), files[0].id).isEmpty())
        } finally { index.discard(scan) }
    }

    @Test fun visualFeatureMigrationPreservesExistingSelectionsAndOldSignatures() {
        for (version in listOf(6, 8)) {
            val db = android.database.sqlite.SQLiteDatabase.create(null)
            try {
                db.execSQL("CREATE TABLE files(id INTEGER PRIMARY KEY,selected INTEGER,retained INTEGER)")
                db.execSQL("INSERT INTO files VALUES(1,0,1)")
                if (version == 8) {
                    db.execSQL("CREATE TABLE similar_signatures(file INTEGER PRIMARY KEY,hash INTEGER)")
                    db.execSQL("INSERT INTO similar_signatures VALUES(1,42)")
                }
                ScanIndex(context).use { it.onUpgrade(db, version, 9) }
                db.rawQuery("SELECT selected,retained FROM files WHERE id=1",null).use {
                    assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)); assertEquals(1,it.getInt(1))
                }
                db.rawQuery("PRAGMA table_info(similar_signatures)",null).use {
                    var found = false
                    while (it.moveToNext()) if(it.getString(it.getColumnIndexOrThrow("name"))=="layout") found=true
                    assertTrue(found)
                }
                if(version==8) db.rawQuery("SELECT hash,layout FROM similar_signatures WHERE file=1",null).use {
                    assertTrue(it.moveToFirst()); assertEquals(42L,it.getLong(0)); assertTrue(it.isNull(1))
                }
            } finally { db.close() }
        }
    }

    @Test
    fun largeGroupPagesDoNotLoadAllMembersAndUnavailableOriginalBlocksSnapshot() =
        runBlocking<Unit> {
            val id = index.start(CleanupFeature.SIMILAR_PHOTOS)
            try {
                index.insert(
                    id,
                    (1..620).map { n ->
                        ScannedFile(
                            uri = "content://similar-test/$n",
                            name = "$n.jpg",
                            mime = "image/jpeg",
                            size = 1000,
                            modifiedMillis = n.toLong(),
                            category = FileCategory.PHOTOS,
                            backend = FileBackend.MEDIA,
                            scope = "test",
                            bucket = "SIMILAR",
                            groupKey = "similar:fixture",
                            retained = n == 1,
                        )
                    },
                )
                val handle = ScanHandle(id, CleanupFeature.SIMILAR_PHOTOS, 620, "Test fixtures")
                // page() already hides unpublished similar groups; use candidate keyset batches
                // instead.
                var after = 0L
                while (true) {
                    val batch = repo.similar.candidates(id, after)
                    if (batch.isEmpty()) break
                    for (f in batch) {
                        repo.similar.signature(f, PhotoSignature(1, 1.0, 100, 30.0, 20.0, 10000))
                        after = f.id
                    }
                }
                repo.similar.rebuild(id)
                index.finishScan(handle)
                val first = repo.similarPaging.page(id, 3, 0, 20)
                assertEquals(20, first.size)
                assertTrue(first.filterIsInstance<SimilarRow.Photos>().all { it.files.size <= 3 })
                val pages = (0..10).flatMap { repo.similarPaging.page(id, 3, it * 20, 20) }
                val photos = pages.filterIsInstance<SimilarRow.Photos>().flatMap { it.files }
                assertEquals(620, photos.size)
                assertEquals(620, photos.map { it.id }.distinct().size)
                assertEquals(619, index.totals(handle, CleanupFilter()).selectedCount)
                assertEquals(
                    619,
                    index.operationCount(index.prepareOperation(handle, CleanupFilter())),
                )
                repo.similar.unavailable(photos.single { it.retained }.id)
                assertEquals(0, index.totals(handle, CleanupFilter()).selectedCount)
                assertEquals(
                    0,
                    index.operationCount(index.prepareOperation(handle, CleanupFilter())),
                )
            } finally {
                index.discard(id)
            }
        }

    @Test
    fun damagedPhotoIsSkippedAndEntirelyDamagedScanFails() =
        runBlocking<Unit> {
            val dir = root()
            var handle: ScanHandle? = null
            try {
                val a = picture(dir, "a.png")
                val b = File(dir, "b.png")
                a.copyTo(b)
                b.setLastModified(a.lastModified())
                val bad = File(dir, "bad.png").apply { writeBytes(ByteArray(200) { 1 }) }
                handle = scan(listOf(a, b, bad).map { file(it, dir) })
                assertEquals(1, handle.analysisSkipped)
                assertEquals(1, index.totals(handle, CleanupFilter()).selectedCount)
                try {
                    scan(listOf(file(bad, dir)))
                    fail("Unreadable-only scan must fail")
                } catch (_: java.io.IOException) {}
            } finally {
                handle?.let { index.discard(it.id) }
                dir.deleteRecursively()
            }
        }

    @Test
    fun entryPermissionsRequireAllFilesLikeVideo() =
        runBlocking<Unit> {
            assertEquals(
                repo.resolveAccess(if (android.os.Build.VERSION.SDK_INT >= 30) CleanupFeature.VIDEOS else CleanupFeature.SCREENSHOTS).request,
                repo.resolveAccess(CleanupFeature.SIMILAR_PHOTOS).request,
            )
        }

    @Test
    fun allFilesAccessDeletesOnlySelectedMediaAndKeepsOriginal() = runBlocking<Unit> {
        org.junit.Assume.assumeTrue(android.os.Environment.isExternalStorageManager())
        val resolver = context.contentResolver
        val dir = root()
        val created = mutableListOf<Uri>()
        val deleted = mutableSetOf<String>()
        var handle: ScanHandle? = null
        var remaining: ScanHandle? = null
        try {
            val picture = picture(dir, "source.png")
            val media = (1..2).map { number ->
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"similar-media-test-${System.nanoTime()}-$number.png")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/SimilarCleanerTests")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
                    })!!
                created += uri
                resolver.openOutputStream(uri)!!.use { output -> picture.inputStream().use { it.copyTo(output) } }
                resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
                resolver.query(uri,arrayOf("_display_name","_size","date_modified"),null,null,null)!!.use { c ->
                    assertTrue(c.moveToFirst())
                    ScannedFile(uri=uri.toString(),name=c.getString(0),mime="image/png",size=c.getLong(1),modifiedMillis=c.getLong(2)*1000,
                        category=FileCategory.PHOTOS,backend=FileBackend.MEDIA,scope="external",width=400,height=300)
                }
            }
            handle = scan(media)
            val original = index.page(handle, CleanupFilter(),0,10).single { it.retained }
            val operation = index.prepareOperation(handle,CleanupFilter())
            val selected = index.operationFiles(operation).map { it.uri }
            assertEquals(1,selected.size)
            val result = container.fileOperations.delete(operation) { _, _ -> }
            assertTrue("Granted manager access should delete without extra Consent",result is OperationStep.Finished)
            val summary = (result as OperationStep.Finished).summary
            if(summary.deleted==1) deleted += selected
            assertEquals(1,summary.deleted)
            resolver.query(Uri.parse(original.uri),arrayOf("_id"),null,null,null)!!.use { assertTrue(it.moveToFirst()) }
            remaining = scan(listOf(original.copy(id=0,groupKey="",bucket="",selected=false,retained=false)))
            assertEquals(0,index.totals(remaining,CleanupFilter()).count)
            assertTrue(repo.similarPaging.page(remaining.id,3,0,20).isEmpty())
        } finally {
            created.filter { it.toString() !in deleted }.forEach { resolver.delete(it,null,null) }
            handle?.let { index.discard(it.id) }; remaining?.let { index.discard(it.id) }
            dir.deleteRecursively()
        }
    }

    @Test
    fun groupPageKeepsSelectionsThroughRecreationAndConfirmsDocumentCopy() =
        runBlocking<Unit> {
            val dir = root()
            var handle: ScanHandle? = null
            try {
                val a = picture(dir, "a.png")
                val b = picture(dir, "b.png", 90)
                val inputs =
                    (1..8).map { n ->
                        val target = File(dir, "photo$n.png")
                        (if (n <= 2) a else b).copyTo(target)
                        target.setLastModified(a.lastModified() + n * 1000)
                        file(target, dir)
                    }
                handle = scan(inputs)
                ActivityScenario.launch<FileCleanupActivity>(
                        Intent(context, FileCleanupActivity::class.java)
                            .putExtra(FileCleanupActivity.EXTRA_SCAN, handle.id)
                            .putExtra(
                                FileCleanupActivity.EXTRA_FEATURE,
                                CleanupFeature.SIMILAR_PHOTOS.name,
                            )
                    )
                    .use { scenario ->
                        fun model(a: FileCleanupActivity) =
                            ViewModelProvider(a)[CleanupViewModel::class.java]
                        waitUntil {
                            var ready = false
                            scenario.onActivity {
                                ready =
                                    model(it).state.value.totals.selectedCount == 6 &&
                                        it.findViewById<RecyclerView>(R.id.cleanup_files)
                                            .childCount > 0
                            }
                            ready
                        }
                        android.os.SystemClock.sleep(
                            250
                        ) // test-only wait for bounded thumbnail decode before capture
                        capture("similar-groups.png")
                        scenario.onActivity {
                            it.findViewById<View>(R.id.cleanup_select_all).performClick()
                        }
                        waitUntil {
                            var done = false
                            scenario.onActivity {
                                done =
                                    model(it).state.value.totals.selectedCount == 0 &&
                                        model(it).state.value.editing == 0
                            }
                            done
                        }
                        scenario.onActivity {
                            assertFalse(it.findViewById<View>(R.id.cleanup_action).isEnabled)
                            model(it).selectAll()
                        }
                        waitUntil {
                            var done = false
                            scenario.onActivity {
                                done =
                                    model(it).state.value.totals.selectedCount == 6 &&
                                        model(it).state.value.editing == 0
                            }
                            done
                        }
                        scenario.recreate()
                        waitUntil {
                            var ready = false
                            scenario.onActivity {
                                ready = model(it).state.value.totals.selectedCount == 6
                            }
                            ready
                        }
                        scenario.onActivity { model(it).prepare() }
                        waitUntil {
                            var shown = false
                            scenario.onActivity {
                                shown =
                                    (it.supportFragmentManager.findFragmentByTag(CleanupMessageDialog.TAG) as? androidx.fragment.app.DialogFragment)?.dialog?.isShowing == true
                            }
                            shown
                        }
                        capture("similar-confirm.png")
                        scenario.onActivity {
                            val dialog =
                                it.supportFragmentManager.findFragmentByTag(
                                    CleanupMessageDialog.TAG
                                )!!
                            val op = model(it).state.value.operation as CleanupOperationState.Confirm
                            assertEquals(it.getString(R.string.similar_confirmation,
                                java.text.NumberFormat.getIntegerInstance().format(op.count),
                                android.text.format.Formatter.formatFileSize(it, op.bytes)),
                                dialog.requireArguments().getString("message"))
                            model(it).dismissOperation()
                            assertEquals(6, model(it).state.value.totals.selectedCount)
                        }
                    }
            } finally {
                handle?.let { index.discard(it.id) }
                dir.deleteRecursively()
            }
        }

    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try {
                val folder =
                    File(context.getExternalFilesDir(null), "similar-tests").apply { mkdirs() }
                File(folder, name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + 15000
        while (!condition() && android.os.SystemClock.uptimeMillis() < end) android.os.SystemClock
            .sleep(25)
        assertTrue("Similar state did not converge", condition())
    }
}
