package com.example.aicleanphonestorage

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.analytics.*
import com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.CleanupViewModel
import com.example.aicleanphonestorage.feature.push.ResidentClickTelemetry
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 注入收集器核验真实选择保存后的数值，不向 SDK 上报模拟文件明细或测试业务事件。 */
@RunWith(AndroidJUnit4::class)
class BusinessMetricsDeviceTest {
    @get:org.junit.Rule val noHotAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    @Test fun selectionReportsUpdatedTotalsAndFilterChangesDoNotRepeatScanResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as CleanApplication).container
        val index = container.fileScanRepository.index
        val directory = File(context.cacheDir, "metric-${System.nanoTime()}").apply { mkdirs() }
        val file = File(directory, "private-document.pdf").apply { writeBytes(ByteArray(1500)) }
        val id = index.start(CleanupFeature.LARGE_FILES)
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        try {
            index.insert(id, listOf(ScannedFile(uri = Uri.fromFile(file).toString(), name = file.name, mime = "application/pdf",
                size = 1500, modifiedMillis = file.lastModified(), category = FileCategory.DOCUMENTS,
                backend = FileBackend.DIRECT, scope = directory.canonicalPath, path = file.canonicalPath)))
            val handle = ScanHandle(id, CleanupFeature.LARGE_FILES, 1, "Test fixture")
            index.finishScan(handle)
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var model: CleanupViewModel
                scenario.onActivity { activity ->
                    model = CleanupViewModel(container.fileScanRepository, container.fileOperations,
                        SavedStateHandle(mapOf("filter.size" to 0L)), id,
                        telemetry = CleanupTelemetry { event, params -> calls += event to params })
                    activity.viewModelStore.put("metrics.test", model)
                }
                waitUntil { var ready = false; scenario.onActivity { ready = model.state.value.totals.count == 1 }; ready }
                scenario.onActivity { model.selectAll() }
                waitUntil { var done = false; scenario.onActivity { done = calls.size == 1 }; done }
                scenario.onActivity {
                    assertEquals(MetricEvent.LARGE_FILE_CHECK, calls.last().first)
                    assertEquals(mapOf("action" to "check", "selected_size" to 0.0015), calls.last().second)
                    model.selectAll()
                }
                waitUntil { var done = false; scenario.onActivity { done = calls.size == 2 }; done }
                scenario.onActivity {
                    assertEquals(mapOf("action" to "uncheck", "selected_size" to 0.0), calls.last().second)
                    model.setFilter(model.state.value.filter.copy(minimumBytes = 50_000_000))
                    assertEquals(MetricEvent.LARGE_FILTER_CHANGE, calls.last().first)
                    val count = calls.size
                    model.setFilter(model.state.value.filter)
                    assertEquals(count, calls.size)
                    assertFalse(calls.any { it.first == MetricEvent.LARGE_SCAN_RESULT })
                    assertTrue(calls.flatMap { it.second.keys }.none { it.contains("name") || it.contains("path") || it.contains("uri") })
                }
            }
        } finally { index.discard(id); directory.deleteRecursively() }
    }
    @Test fun residentMetadataIsConsumedOnceAndOrdinaryPushIsNotMiscounted() {
        assertNull(ResidentClickTelemetry.take(Intent().putExtra("notification.destination", "clean")))
        val intent = Intent().putExtra(ResidentClickTelemetry.ENTRY, "clean").putExtra(ResidentClickTelemetry.BADGE, "shown")
        assertEquals(mapOf("entry" to "clean", "clean_badge" to "shown"), ResidentClickTelemetry.take(intent))
        assertNull(ResidentClickTelemetry.take(intent))
        assertNull(ResidentClickTelemetry.take(Intent().putExtra(ResidentClickTelemetry.ENTRY, "private-package-name").putExtra(ResidentClickTelemetry.BADGE, "shown")))
    }
    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(30)
        assertTrue(condition())
    }
}
