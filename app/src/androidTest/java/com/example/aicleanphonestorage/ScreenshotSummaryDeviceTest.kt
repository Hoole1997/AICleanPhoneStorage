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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只使用独立扫描索引样例，不读取或清理用户截图。 */
@RunWith(AndroidJUnit4::class)
class ScreenshotSummaryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun selectionWritesOnlyChangeCapacityWithoutFlashingButtonState() {
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.Theme_AICleanPhoneStorage)
            val binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed))
            val renderer = CleanupActionRenderer(binding)
            val initial = CleanupUiState(
                handle = ScanHandle(1, CleanupFeature.SCREENSHOTS, 15, "Test fixtures"),
                totals = SelectionTotals(count = 15, bytes = 27_900_000, selectedCount = 2, selectedBytes = 3_500_000),
                totalsReady = true,
            )
            renderer.render(initial)
            val background = binding.cleanupAction.background
            val colors = binding.cleanupAction.backgroundTintList
            val textChanges = mutableListOf<String>()
            binding.cleanupAction.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) { textChanges += s.toString() }
            })
            val updated = initial.copy(totals = initial.totals.copy(selectedCount = 3, selectedBytes = 4_200_000))
            for (state in listOf(initial.copy(editing = 1), updated.copy(editing = 1), updated, updated)) {
                renderer.render(state)
                assertTrue(binding.cleanupAction.isEnabled)
                assertEquals(state.editing == 0, binding.cleanupAction.isClickable)
                assertSame(background, binding.cleanupAction.background)
                assertSame(colors, binding.cleanupAction.backgroundTintList)
            }
            assertEquals(1, textChanges.size)
            assertTrue(textChanges.single().contains("4.2 MB"))
            renderer.render(updated.copy(totals = updated.totals.copy(selectedCount = 0, selectedBytes = 0)))
            assertFalse(binding.cleanupAction.isEnabled)
            assertFalse(binding.cleanupAction.isClickable)
            assertEquals(themed.getString(R.string.cleanup_clean), binding.cleanupAction.text.toString())
        }
    }

    @Test fun subtitleUsesEntireIndexAndSurvivesSelectionAndRecreation() {
        val index = (context.applicationContext as CleanApplication).container.fileScanRepository.index
        val id = index.start(CleanupFeature.SCREENSHOTS)
        try {
            index.insert(id, (1..150).map { number -> ScannedFile(
                uri = "file:///screenshot-summary-fixtures/$number", name = "Screenshot_$number",
                mime = "application/octet-stream", size = 186_000, modifiedMillis = 1,
                category = FileCategory.OTHER, backend = FileBackend.DIRECT, scope = "/screenshot-summary-fixtures",
            ) })
            index.finishScan(ScanHandle(id, CleanupFeature.SCREENSHOTS, 150, "Test fixtures"))
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.SCREENSHOTS.name)).use { scenario ->
                var original = ""
                waitUntil {
                    var ready = false
                    scenario.onActivity {
                        val state = ViewModelProvider(it)[CleanupViewModel::class.java].state.value
                        ready = state.totalsReady
                        if (ready) {
                            val subtitle = it.findViewById<TextView>(R.id.cleanup_screenshot_summary)
                            assertEquals(View.VISIBLE, subtitle.visibility)
                            original = subtitle.text.toString()
                            assertTrue(original.contains("150"))
                            assertTrue(original.contains("27.9"))
                            assertFalse(it.findViewById<View>(R.id.cleanup_action).isEnabled)
                            assertEquals(27_900_000L, state.totals.bytes)
                        }
                    }
                    ready
                }
                scenario.onActivity { it.findViewById<View>(R.id.cleanup_select_all).performClick() }
                waitUntil {
                    var selected = false
                    scenario.onActivity {
                        val state = ViewModelProvider(it)[CleanupViewModel::class.java].state.value
                        selected = state.totals.selectedCount == 150 &&
                            it.findViewById<TextView>(R.id.cleanup_action).text.contains("27.9 MB")
                        assertEquals(original, it.findViewById<TextView>(R.id.cleanup_screenshot_summary).text.toString())
                    }
                    selected
                }
                val captions = mutableListOf<String>()
                scenario.onActivity {
                    it.findViewById<TextView>(R.id.cleanup_action).addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: android.text.Editable?) { captions += s.toString() }
                    })
                }
                val handle = index.handle(id)!!
                val first = index.page(handle, CleanupFilter(), 0, 1).single().id
                scenario.onActivity { ViewModelProvider(it)[CleanupViewModel::class.java].toggle(first, false) }
                waitUntil {
                    var ready = false
                    scenario.onActivity {
                        ready = it.findViewById<TextView>(R.id.cleanup_action).text.contains("27.7 MB")
                        assertEquals(original, it.findViewById<TextView>(R.id.cleanup_screenshot_summary).text.toString())
                    }
                    ready
                }
                assertEquals("Activity must not reset the caption to Clean between updates", 1, captions.size)
                assertTrue(captions.single().contains("27.7 MB"))
                // 模拟清理引擎移除一项后的索引变化，副标题应反映剩余总量。
                index.remove(first)
                scenario.onActivity { ViewModelProvider(it)[CleanupViewModel::class.java].refreshTotals() }
                scenario.recreate()
                waitUntil {
                    var ready = false
                    scenario.onActivity {
                        val state = ViewModelProvider(it)[CleanupViewModel::class.java].state.value
                        ready = state.totalsReady && state.totals.count == 149
                        if (ready) assertTrue(it.findViewById<TextView>(R.id.cleanup_screenshot_summary).text.contains("149"))
                    }
                    ready
                }
            }
        } finally { index.discard(id) }
    }

    @Test fun subtitleMatchesReferenceAndFitsLargeFonts() {
        for ((language, scale) in listOf("en" to 1f, "zh-Hans" to 1f, "zh-Hans" to 2f)) {
            instrumentation.runOnMainSync {
                val config = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language)); fontScale = scale
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
                val binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed))
                binding.cleanupTitle.setText(R.string.cleanup_screenshots)
                val renderer = CleanupListStateRenderer(binding)
                val initial = CleanupUiState(handle = ScanHandle(1, CleanupFeature.SCREENSHOTS, 15, "Test fixtures"))
                renderer.state(initial)
                assertEquals(View.GONE, binding.cleanupScreenshotSummary.visibility)
                renderer.state(initial.copy(totals = SelectionTotals(count = 15, bytes = 27_900_000), totalsReady = true))
                assertTrue(binding.cleanupScreenshotSummary.text.startsWith(if (language == "en") "15 screenshots" else "15 张截图"))
                assertTrue(binding.cleanupScreenshotSummary.text.contains("27.9 MB"))
                assertFalse(binding.cleanupAction.isEnabled)
                val width = ((if (scale > 1) 320 else 375) * themed.resources.displayMetrics.density).toInt()
                val height = (640 * themed.resources.displayMetrics.density).toInt()
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, width, height)
                val subtitle = binding.cleanupScreenshotSummary
                assertTrue(subtitle.height >= subtitle.layout.height)
                assertTrue(binding.cleanupTitle.bottom <= subtitle.top)
                for (line in 0 until subtitle.layout.lineCount) assertTrue(subtitle.layout.getLineWidth(line) <= subtitle.width)
                val image = Bitmap.createBitmap(width, subtitle.bottom + (16 * themed.resources.displayMetrics.density).toInt(), Bitmap.Config.ARGB_8888)
                try {
                    binding.root.draw(Canvas(image))
                    val folder = File(context.getExternalFilesDir(null), "screenshot-summary-tests").apply { mkdirs() }
                    File(folder, "subtitle_${language}_$scale.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { image.recycle() }
                val totals = SelectionTotals(count = 15, bytes = 27_900_000, selectedCount = 5, selectedBytes = 3_500_000)
                val selected = initial.copy(totals = totals, totalsReady = true)
                val disabledColor = binding.cleanupAction.backgroundTintList!!.getColorForState(binding.cleanupAction.drawableState, 0)
                renderer.pagesPresented(15)
                renderer.state(selected)
                assertTrue(binding.cleanupAction.isEnabled)
                assertTrue(binding.cleanupAction.text.contains("3.5 MB"))
                assertTrue(binding.cleanupScreenshotSummary.text.contains("27.9 MB"))
                val enabledColor = binding.cleanupAction.backgroundTintList!!.getColorForState(binding.cleanupAction.drawableState, 0)
                assertNotEquals(disabledColor, enabledColor)
                // 只画当前按钮状态；既验证灰色禁用态，也验证选择容量和大字号自然高度。
                for (hasSelection in listOf(false, true)) {
                    renderer.state(if (hasSelection) selected else initial.copy(totals = SelectionTotals(count = 15, bytes = 27_900_000), totalsReady = true))
                    binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    binding.root.layout(0, 0, width, height)
                    val button = binding.cleanupAction
                    assertTrue(button.height >= button.layout.height)
                    val frame = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
                    try {
                        button.draw(Canvas(frame))
                        val folder = File(context.getExternalFilesDir(null), "screenshot-summary-tests").apply { mkdirs() }
                        File(folder, "button_${language}_${scale}_$hasSelection.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { frame.recycle() }
                }
                renderer.state(selected.copy(editing = 1))
                assertTrue(binding.cleanupAction.isEnabled)
                assertFalse(binding.cleanupAction.isClickable)
                renderer.state(initial.copy(totalsReady = true))
                assertTrue(subtitle.text.startsWith("0"))
                renderer.state(initial.copy(handle = initial.handle!!.copy(feature = CleanupFeature.LARGE_FILES), totalsReady = true))
                assertEquals(View.GONE, subtitle.visibility)
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
        assertTrue("Screenshot summary did not update", condition())
    }
}
