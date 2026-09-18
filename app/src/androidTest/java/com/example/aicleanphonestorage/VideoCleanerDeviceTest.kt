package com.example.aicleanphonestorage

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.coroutines.*
import com.example.aicleanphonestorage.databinding.ItemVideoMonthBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.videos.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅生成独立测试扫描索引；不读取、选择或删除任何用户媒体。 */
@RunWith(AndroidJUnit4::class)
class VideoCleanerDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = (context.applicationContext as CleanApplication).container.fileScanRepository
    private val index get() = repository.index
    private fun fixture(n: Int) = ScannedFile(uri="content://video-test-fixtures/$n", name="test_$n.mp4", mime="video/mp4",
        size=3_200_000, modifiedMillis=1_780_000_000_000L + n * 1000, category=FileCategory.VIDEOS,
        backend=FileBackend.MEDIA, scope="test", bucket=if (n <= 130) "2026-07" else "2026-06")

    @Test fun paginationSelectionCollapseAndRefreshKeepIndependentScopes() = runBlocking {
        val id = index.start(CleanupFeature.VIDEOS)
        val other = index.start(CleanupFeature.VIDEOS)
        val staging = index.start(CleanupFeature.VIDEOS)
        try {
            val handle = ScanHandle(id, CleanupFeature.VIDEOS, 160, "Test fixtures")
            index.insert(id, (1..160).map(::fixture)); index.finishScan(handle)
            index.insert(other, listOf(fixture(999))); index.finishScan(ScanHandle(other, CleanupFeature.VIDEOS, 1, "Test fixtures"))
            assertEquals(160, index.totals(handle, CleanupFilter()).selectedCount)
            val pages = (0..2).flatMap { repository.videos.page(id, it * 60, 60) }
            assertEquals(162, pages.size)
            assertEquals(listOf("2026-07", "2026-06"), pages.filterIsInstance<VideoRow.Month>().map { it.month })
            assertEquals(160, pages.filterIsInstance<VideoRow.Video>().map { it.file.id }.distinct().size)
            val first = pages.filterIsInstance<VideoRow.Video>().first().file
            index.select(first.id, false); index.finishScan(handle)
            assertEquals(159, index.totals(handle, CleanupFilter()).selectedCount)
            repository.videos.collapse(id, "2026-07", true)
            assertEquals(32, repository.videos.page(id, 0, 60).size)
            assertEquals(159, index.totals(handle, CleanupFilter()).selectedCount)
            index.selectAll(handle, CleanupFilter(bucket="2026-07"), false)
            assertEquals(30, index.totals(handle, CleanupFilter()).selectedCount)
            val op = index.prepareOperation(handle, CleanupFilter())
            assertEquals(30, index.operationCount(op))
            assertTrue(index.operationFiles(op).all { it.bucket == "2026-06" })
            index.insert(staging, listOf(fixture(130), fixture(150), fixture(1000)))
            repository.videos.reconcile(id, staging)
            assertEquals(3, index.totals(handle, CleanupFilter()).count)
            assertEquals(1, index.totals(handle, CleanupFilter()).selectedCount)
            assertEquals(1, index.totals(ScanHandle(other, CleanupFeature.VIDEOS, 1, "Test fixtures"), CleanupFilter()).count)
        } finally { index.discard(id); index.discard(other); index.discard(staging) }
    }

    @Test fun actualPageSelectAllMonthToggleConfirmationAndRecreation() {
        val id = index.start(CleanupFeature.VIDEOS)
        try {
            index.insert(id, (1..12).map { fixture(it).copy(bucket=if (it <= 9) "2026-07" else "2026-06") })
            index.finishScan(ScanHandle(id, CleanupFeature.VIDEOS, 12, "Test fixtures"))
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, id).putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.VIDEOS.name)).use { scenario ->
                fun model(a: FileCleanupActivity) = ViewModelProvider(a)[CleanupViewModel::class.java]
                waitUntil { var ready=false; scenario.onActivity { ready=model(it).state.value.totals.selectedCount==12 && it.findViewById<RecyclerView>(R.id.cleanup_files).childCount>0 }; ready }
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    val folder=File(context.getExternalFilesDir(null),"video-tests").apply{mkdirs()}
                    try { File(folder,"video_hardware.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
                    finally { bitmap.recycle() }
                }
                scenario.onActivity { a ->
                    assertEquals("Videos", a.findViewById<TextView>(R.id.cleanup_title).text.toString())
                    assertTrue(a.findViewById<View>(R.id.cleanup_action).isEnabled)
                    save(a.window.decorView, "video_page.png")
                    a.findViewById<View>(R.id.cleanup_select_all).performClick()
                }
                waitUntil { var done=false; scenario.onActivity { done=model(it).state.value.totals.selectedCount==0 && model(it).state.value.editing==0 }; done }
                scenario.onActivity { a ->
                    assertFalse(a.findViewById<View>(R.id.cleanup_action).isEnabled)
                    model(a).selectBucket("2026-07", true)
                }
                waitUntil { var done=false; scenario.onActivity { done=model(it).state.value.totals.selectedCount==9 && model(it).state.value.editing==0 }; done }
                scenario.recreate()
                waitUntil { var done=false; scenario.onActivity { done=model(it).state.value.totals.selectedCount==9 }; done }
                scenario.onActivity { model(it).prepare() }
                waitUntil { var done=false; scenario.onActivity { done=model(it).state.value.operation is CleanupOperationState.Confirm }; done }
                scenario.onActivity {
                    assertEquals(9, (model(it).state.value.operation as CleanupOperationState.Confirm).count)
                    model(it).dismissOperation()
                    assertEquals(9, model(it).state.value.totals.selectedCount)
                }
            }
        } finally { index.discard(id) }
    }

    @Test fun monthHeaderFitsSmallScreenAndDoubleFont() {
        for (scale in listOf(1f, 2f)) instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply { fontScale=scale; setLocale(java.util.Locale.ENGLISH) }
            val themed=ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val binding=ItemVideoMonthBinding.inflate(LayoutInflater.from(themed))
            binding.videoMonth.text="2026-07"; binding.videoMonthSummary.text="120 files/384.0 MB"
            val width=((if (scale==1f) 343 else 288)*themed.resources.displayMetrics.density).toInt()
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            binding.root.layout(0,0,width,binding.root.measuredHeight)
            for (view in listOf(binding.videoMonth,binding.videoMonthSummary,binding.videoMonthAll)) {
                assertTrue(view.height >= view.layout.height + view.compoundPaddingTop + view.compoundPaddingBottom)
                for (line in 0 until view.layout.lineCount) assertTrue(view.layout.getLineWidth(line)<=view.width-view.compoundPaddingLeft-view.compoundPaddingRight)
            }
            save(binding.root,"video_header_$scale.png")
            val tile=com.example.aicleanphonestorage.databinding.ItemVideoBinding.inflate(LayoutInflater.from(themed))
            tile.videoSize.text="3.2 MB"
            val columns=if(scale>=1.5f) 2 else 3
            val tileWidth=(width-(columns-1)*6*themed.resources.displayMetrics.density).toInt()/columns
            tile.root.measure(View.MeasureSpec.makeMeasureSpec(tileWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            tile.root.layout(0,0,tileWidth,tile.root.measuredHeight)
            assertTrue(tile.videoSize.height>=tile.videoSize.layout.height)
            assertTrue(tile.videoSize.right<=tile.videoCheck.left)
            assertTrue(tile.videoSize.top>=tile.videoPlay.bottom)
            save(tile.root,"video_tile_$scale.png")
        }
    }
    private fun save(view: View,name: String) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(bitmap)); val folder=File(context.getExternalFilesDir(null),"video-tests").apply{mkdirs()}
            File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally { bitmap.recycle() }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline=android.os.SystemClock.uptimeMillis()+12000
        while (!condition() && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(40)
        assertTrue("Video state did not converge",condition())
    }
}
