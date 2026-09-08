package com.example.aicleanphonestorage

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.databinding.ScreenCompletionBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.FileCleanupActivity
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 删除/压缩测试只处理本测试创建的内容；不切换设备权限，不触碰用户文件。 */
@RunWith(AndroidJUnit4::class)
class CompletionDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as CleanApplication).container

    private val index
        get() = container.fileScanRepository.index

    @Test
    fun layoutsSupportLargeFontsAndMotionStopsWithoutReplay() {
        val cases =
            listOf(
                CompletionReport(CompletionKind.CLEANUP, 28, freedBytes = 256_000_000),
                CompletionReport(
                    CompletionKind.COMPRESSION,
                    12,
                    reducedBytes = 10_000_000,
                    originalsRemaining = 12,
                    removableOriginals = 12,
                    operationId = 1,
                ),
                CompletionReport(CompletionKind.CLEANUP, 1, failed = 2, skipped = 1),
                CompletionReport(CompletionKind.CLEANUP, 0, skipped = 2),
                CompletionReport(CompletionKind.NOTIFICATIONS, 4),
            )
        for ((number, report) in cases.withIndex()) for (fontScale in listOf(1f, 2f)) {
            instrumentation.runOnMainSync {
                val config =
                    Configuration(context.resources.configuration).apply {
                        this.fontScale = fontScale
                    }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(config),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val binding = ScreenCompletionBinding.inflate(LayoutInflater.from(themed))
                CompletionRenderer(binding).render(report)
                val density = themed.resources.displayMetrics.density
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        (375 * density).toInt(),
                        View.MeasureSpec.EXACTLY,
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        (740 * density).toInt(),
                        View.MeasureSpec.EXACTLY,
                    ),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                for (text in
                    listOf(
                        binding.completionTitle,
                        binding.completionCount,
                        binding.completionDetail,
                    )) {
                    if (text.visibility == View.VISIBLE)
                        assertTrue(text.height >= text.layout.height)
                }
                val motion = CompletionMotion(binding, false)
                motion.update(true, true, true)
                assertTrue(motion.running)
                motion.update(true, false, true)
                assertFalse(motion.running)
                assertEquals(1f, binding.completionResult.alpha)
                motion.update(true, true, true)
                assertFalse(motion.running)
                val bitmap =
                    Bitmap.createBitmap(
                        binding.root.width,
                        binding.root.height,
                        Bitmap.Config.ARGB_8888,
                    )
                binding.root.draw(Canvas(bitmap))
                val directory =
                    File(context.getExternalFilesDir(null), "completion-tests").apply { mkdirs() }
                File(directory, "result_${number}_$fontScale.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                binding.completionScroll.fullScroll(View.FOCUS_DOWN)
                assertTrue(binding.completionContinue.height >= 48 * density)
            }
        }
        ActivityScenario.launch<CompletionActivity>(
                CompletionContract.intent(context, cases.first())
            )
            .use { scenario ->
                scenario.recreate()
                onView(withId(R.id.completion_count)).check(matches(withText("28")))
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                onView(withId(R.id.completion_title))
                    .check(matches(withText(R.string.completion_cleanup_title)))
            }
    }

    @Test
    fun fileDeletionOpensCommonResultAndBackDoesNotReopenIt() {
        runFileFlow(compress = false)
    }

    @Test
    fun compressionKeepsOriginalUntilSeparateConfirmationAndReportsNetSpace() {
        runFileFlow(compress = true)
    }

    private fun runFileFlow(compress: Boolean) {
        val root = File(context.cacheDir, "completion_test_${UUID.randomUUID()}").apply { mkdirs() }
        var scan: ScanHandle? = null
        var output: Uri? = null
        try {
            val file = File(root, if (compress) "sample.png" else "sample.pdf")
            if (compress) {
                val bitmap = Bitmap.createBitmap(320, 400, Bitmap.Config.ARGB_8888)
                val random = java.util.Random(42)
                val pixels =
                    IntArray(320 * 400) {
                        Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    }
                bitmap.setPixels(pixels, 0, 320, 0, 0, 320, 400)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            } else java.io.RandomAccessFile(file, "rw").use { it.setLength(12_000_000) }
            val sourceBytes = file.length()
            val feature =
                if (compress) CleanupFeature.PHOTO_COMPRESS else CleanupFeature.LARGE_FILES
            val id = index.start(feature)
            scan = ScanHandle(id, feature, 1, "Completion test")
            index.insert(
                id,
                listOf(
                    ScannedFile(
                        uri = Uri.fromFile(file).toString(),
                        name = file.name,
                        mime = if (compress) "image/png" else "application/pdf",
                        size = sourceBytes,
                        modifiedMillis = file.lastModified(),
                        category = if (compress) FileCategory.PHOTOS else FileCategory.DOCUMENTS,
                        backend = FileBackend.DIRECT,
                        scope = root.canonicalPath,
                        path = file.canonicalPath,
                    )
                ),
            )
            index.finishScan(scan)
            ActivityScenario.launch<FileCleanupActivity>(
                    Intent(context, FileCleanupActivity::class.java)
                        .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                )
                .use { scenario ->
                    waitUntil {
                        var ready = false
                        scenario.onActivity {
                            ready = it.findViewById<View>(R.id.cleanup_select_all).isEnabled
                        }
                        ready
                    }
                    onView(withId(R.id.cleanup_select_all)).perform(click())
                    waitUntil {
                        var ready = false
                        scenario.onActivity {
                            ready = it.findViewById<View>(R.id.cleanup_action).isEnabled
                        }
                        ready
                    }
                    onView(withId(R.id.cleanup_action)).perform(click())
                    onView(withId(R.id.confirm_accept)).perform(click())
                    waitUntil { resumedCompletion() != null }
                    var report: CompletionReport? = null
                    instrumentation.runOnMainSync {
                        report = CompletionContract.read(resumedCompletionOnMain()!!.intent)
                    }
                    assertEquals(1, report!!.completed)
                    if (compress) {
                        assertTrue(file.exists())
                        assertEquals(0L, report!!.freedBytes)
                        val row = index.operationFiles(report!!.operationId, "copied").single()
                        output = Uri.parse(index.output(report!!.operationId, row.id)!!.first)
                        assertTrue(report!!.reducedBytes > 0)
                        onView(withId(R.id.completion_originals)).perform(scrollTo(), click())
                        onView(withId(R.id.confirm_title)).check(matches(isDisplayed()))
                        assertTrue(file.exists())
                        onView(withId(R.id.confirm_accept)).perform(click())
                        waitUntil { resumedCompletion() != null }
                        instrumentation.runOnMainSync {
                            report = CompletionContract.read(resumedCompletionOnMain()!!.intent)
                        }
                        assertEquals(1, report!!.completed)
                        assertEquals(0, report!!.originalsRemaining)
                        val copyBytes =
                            context.contentResolver.openAssetFileDescriptor(output!!, "r")!!.use {
                                it.length
                            }
                        assertEquals(sourceBytes - copyBytes, report!!.freedBytes)
                    } else assertEquals(sourceBytes, report!!.freedBytes)
                    assertFalse(file.exists())
                    onView(withId(R.id.completion_back)).perform(click())
                    waitUntil { resumedCompletion() == null }
                    scenario.recreate()
                    instrumentation.waitForIdleSync()
                    assertNull(resumedCompletion())
                }
        } finally {
            instrumentation.runOnMainSync { resumedCompletionOnMain()?.finish() }
            output?.let { context.contentResolver.delete(it, null, null) }
            scan?.let { index.discard(it.id) }
            root.deleteRecursively()
        }
    }

    private fun resumedCompletionOnMain() =
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<CompletionActivity>()
            .firstOrNull()

    private fun resumedCompletion(): CompletionActivity? {
        var result: CompletionActivity? = null
        instrumentation.runOnMainSync { result = resumedCompletionOnMain() }
        return result
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue("Completion flow timed out", check())
    }
}
