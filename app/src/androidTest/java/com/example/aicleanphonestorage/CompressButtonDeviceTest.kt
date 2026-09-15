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

/** 只使用索引样例与原生按钮，不压缩或读取用户照片。 */
@RunWith(AndroidJUnit4::class)
class CompressButtonDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun editingKeepsButtonAppearanceAndOnlyCountChangesCaption() {
        for ((language, scale) in listOf("en" to 1f, "zh-Hans" to 2f)) instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language)); fontScale = scale
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(themed))
            val renderer = CleanupActionRenderer(binding)
            val empty = CleanupUiState(handle = ScanHandle(1, CleanupFeature.PHOTO_COMPRESS, 150, "Test fixtures"), totalsReady = true)
            renderer.render(empty)
            val button = binding.cleanupAction
            assertEquals(themed.getString(R.string.cleanup_compress), button.text.toString())
            assertFalse(button.isEnabled); assertFalse(button.isClickable)
            val disabled = button.backgroundTintList!!.getColorForState(button.drawableState, 0)
            val selected = empty.copy(totals = SelectionTotals(count = 150, selectedCount = 3, selectedBytes = 12_000_000))
            renderer.render(selected)
            assertEquals(themed.getString(R.string.cleanup_compress_selected, "3"), button.text.toString())
            assertTrue(button.isEnabled)
            assertNotEquals(disabled, button.backgroundTintList!!.getColorForState(button.drawableState, 0))
            val background = button.background
            val colors = button.backgroundTintList
            val changes = mutableListOf<String>()
            button.addTextChangedListener(watcher(changes))
            val four = selected.copy(totals = selected.totals.copy(selectedCount = 4))
            for (state in listOf(selected.copy(editing = 1), four.copy(editing = 1), four, four.copy(totals = four.totals.copy(selectedBytes = 20_000_000)))) {
                renderer.render(state)
                assertTrue(button.isEnabled)
                assertEquals(state.editing == 0, button.isClickable)
                assertSame(background, button.background)
                assertSame(colors, button.backgroundTintList)
            }
            assertEquals(listOf(themed.getString(R.string.cleanup_compress_selected, "4")), changes)
            // 大字体及多位数量自然测量，导出选中/未选中两个原生状态。
            for (count in listOf(0, 150)) {
                renderer.render(four.copy(totals = four.totals.copy(selectedCount = count)))
                val width = (260 * themed.resources.displayMetrics.density).toInt()
                button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                button.layout(0, 0, width, button.measuredHeight)
                assertTrue(button.height >= button.layout.height)
                for (line in 0 until button.layout.lineCount) assertEquals(0, button.layout.getEllipsisCount(line))
                val image = Bitmap.createBitmap(width, button.height, Bitmap.Config.ARGB_8888)
                try {
                    button.draw(Canvas(image))
                    val folder = File(context.getExternalFilesDir(null), "compress-button-tests").apply { mkdirs() }
                    File(folder, "compress_${language}_${scale}_$count.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { image.recycle() }
            }
            renderer.render(four.copy(operation = CleanupOperationState.Running(1)))
            assertFalse(button.isEnabled); assertFalse(button.isClickable)
        }
    }

    @Test fun pageShowsFullSelectedCountAcrossPagingAndRecreationWithoutTransientReset() {
        val index = (context.applicationContext as CleanApplication).container.fileScanRepository.index
        val id = index.start(CleanupFeature.PHOTO_COMPRESS)
        try {
            index.insert(id, (1..150).map { number -> ScannedFile(
                uri = "file:///compress-button-fixtures/$number", name = "Photo_$number",
                mime = "application/octet-stream", size = 100_000, modifiedMillis = 1,
                category = FileCategory.OTHER, backend = FileBackend.DIRECT, scope = "/compress-button-fixtures",
            ) })
            val handle = ScanHandle(id, CleanupFeature.PHOTO_COMPRESS, 150, "Test fixtures").also(index::finishScan)
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.PHOTO_COMPRESS.name)).use { scenario ->
                waitUntil {
                    var ready = false
                    scenario.onActivity {
                        ready = ViewModelProvider(it)[CleanupViewModel::class.java].state.value.totalsReady
                        assertFalse(it.findViewById<View>(R.id.cleanup_action).isEnabled)
                    }
                    ready
                }
                scenario.onActivity { it.findViewById<View>(R.id.cleanup_select_all).performClick() }
                fun captionContains(count: String): Boolean {
                    var ready = false
                    scenario.onActivity { ready = it.findViewById<TextView>(R.id.cleanup_action).let { button -> button.isEnabled && button.text.contains(count) } }
                    return ready
                }
                waitUntil { captionContains("150") }
                scenario.onActivity { assertTrue(it.findViewById<TextView>(R.id.cleanup_potential).text.contains("15.0 MB")) }
                val changes = mutableListOf<String>()
                scenario.onActivity {
                    it.findViewById<TextView>(R.id.cleanup_action).addTextChangedListener(watcher(changes))
                    ViewModelProvider(it)[CleanupViewModel::class.java].toggle(index.page(handle, CleanupFilter(), 0, 1).first().id, false)
                }
                waitUntil { captionContains("149") }
                assertEquals(1, changes.size)
                assertTrue(changes.single().contains("149"))
                scenario.onActivity { assertTrue(it.findViewById<TextView>(R.id.cleanup_potential).text.contains("14.9 MB")) }
                scenario.recreate()
                waitUntil { captionContains("149") }
                scenario.onActivity { it.findViewById<View>(R.id.cleanup_select_all).performClick() }
                waitUntil { captionContains("150") }
                scenario.onActivity { it.findViewById<View>(R.id.cleanup_select_all).performClick() }
                waitUntil {
                    var cleared = false
                    scenario.onActivity { cleared = it.findViewById<TextView>(R.id.cleanup_action).let { button -> !button.isEnabled && button.text == it.getString(R.string.cleanup_compress) } }
                    cleared
                }
            }
        } finally { index.discard(id) }
    }

    private fun watcher(changes: MutableList<String>) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) { changes += s.toString() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
        assertTrue("Compression selection did not update", condition())
    }
}
