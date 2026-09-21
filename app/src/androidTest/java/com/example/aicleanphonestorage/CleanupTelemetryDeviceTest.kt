package com.example.aicleanphonestorage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立索引与假 URI，仅准备选择快照；不运行广告、真实扫描或删除。 */
@RunWith(AndroidJUnit4::class)
class CleanupTelemetryDeviceTest {
    @Test fun monthSelectionUsesPageTotalAndConfirmationUsesFrozenSnapshotOnlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = (instrumentation.targetContext.applicationContext as CleanApplication).container
        val index = container.fileScanRepository.index
        val scan = index.start(CleanupFeature.VIDEOS)
        val events = CopyOnWriteArrayList<Pair<MetricEvent, Map<String, Any>>>()
        val store = ViewModelStore()
        lateinit var model: CleanupViewModel
        try {
            index.insert(scan, (1..3).map { n -> ScannedFile(
                uri = "content://telemetry-test/$n", name = "test$n.mp4", mime = "video/mp4",
                size = 1_500_000, modifiedMillis = 1_780_000_000_000,
                category = FileCategory.VIDEOS, backend = FileBackend.MEDIA, scope = "test",
                bucket = if (n < 3) "2026-08" else "2026-07",
            ) })
            index.finishScan(ScanHandle(scan, CleanupFeature.VIDEOS, 3, "Test"))
            instrumentation.runOnMainSync {
                model = CleanupViewModel(container.fileScanRepository, container.fileOperations,
                    SavedStateHandle(), scan, telemetry = CleanupTelemetry { event, params -> events += event to params })
                store.put("test", model)
            }
            await { model.state.value.totalsReady && model.state.value.totals.count == 3 }
            instrumentation.runOnMainSync { model.selectBucket("2026-08", false) }
            await { events.any { it.first == MetricEvent.VIDEO_CHECK } && model.state.value.editing == 0 }
            assertEquals(mapOf("action" to "uncheck", "selected_size" to 1.5), events.first().second)
            instrumentation.runOnMainSync { model.prepare() }
            await { model.state.value.operation is CleanupOperationState.Confirm }
            val first = model.state.value.operation as CleanupOperationState.Confirm
            instrumentation.runOnMainSync {
                model.reportConfirmation(first.id + 100, true) // 迟到/错误操作不应记录。
                model.reportConfirmation(first.id, true)
                model.reportConfirmation(first.id, true)
                model.reportConfirmation(first.id, false)
                model.dismissOperation()
                model.reportConfirmation(first.id, true)
            }
            assertEquals(1, events.count { it.first == MetricEvent.VIDEO_CONFIRM_CLEAN })
            assertEquals(mapOf("selected_size" to 1.5), events.last().second)
            assertFalse(events.any { it.first == MetricEvent.VIDEO_CANCEL_CLEAN })
            await { model.state.value.operation == CleanupOperationState.Idle && model.state.value.totalsReady }
            instrumentation.runOnMainSync { model.prepare() }
            await { model.state.value.operation is CleanupOperationState.Confirm }
            val second = model.state.value.operation as CleanupOperationState.Confirm
            instrumentation.runOnMainSync {
                model.reportConfirmation(second.id, false)
                model.dismissOperation()
            }
            assertEquals(MetricEvent.VIDEO_CANCEL_CLEAN to emptyMap<String, Any>(), events.last())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            index.discard(scan)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Timed out waiting for telemetry state", condition())
    }
}
