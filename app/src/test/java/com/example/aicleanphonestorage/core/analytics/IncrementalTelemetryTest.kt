package com.example.aicleanphonestorage.core.analytics

import com.example.aicleanphonestorage.feature.filecleaner.analytics.CleanupTelemetry
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.feature.malware.analytics.MalwareTelemetry
import com.example.aicleanphonestorage.feature.malware.data.*
import com.example.aicleanphonestorage.feature.malware.ui.*
import com.example.aicleanphonestorage.feature.battery.data.*
import com.example.aicleanphonestorage.feature.battery.ui.*
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** 验证飞书协议、真实状态时序及取消/重复回调；不连接上报服务器或真实扫描 SDK。 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncrementalTelemetryTest {
    private val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
    private val sink = EventSink { event, params -> events += event to params }

    @Test fun protocolPageNamesRemainSeparateFromInternalFeatureNames() {
        mapOf("video" to "vedio", "video_result" to "vedion_result",
            "duplicate" to "duplicatephoto", "duplicate_result" to "duplicatephoto_result",
            "malware" to "MalwareScan", "malware_result" to "MalwareScan_result",
            "battery" to "BatteryInfo", "junk" to "junk").forEach { (key, value) ->
            assertEquals(value, BusinessPageNames.wire(key))
        }
        assertEquals("video", CleanupTelemetry.page(CleanupFeature.VIDEOS))
    }

    @Test fun bothMediaFeaturesCoverAllSixEventsWithExactNumericFields() {
        val telemetry = CleanupTelemetry(sink)
        for ((feature, prefix) in listOf(CleanupFeature.VIDEOS to "video", CleanupFeature.SIMILAR_PHOTOS to "duplicatephoto")) {
            events.clear()
            val totals = SelectionTotals(3, 4_500_000, 2, 2_500_000)
            telemetry.scan(feature, totals)
            telemetry.selection(feature, null, false, totals)
            telemetry.cleanClick(feature, totals)
            telemetry.confirmation(feature, true, 2_500_000)
            telemetry.confirmation(feature, false, 2_500_000)
            telemetry.result(feature, CompletionReport(CompletionKind.CLEANUP, 1, freedBytes = 1_250_000))
            assertEquals(listOf("scan_result", "check", "clean_click", "confirm_clean", "cancel_clean", "result_show")
                .map { "${prefix}_$it" }, events.map { it.first.wireName })
            assertEquals(listOf(mapOf("count" to 3, "total_size" to 4.5),
                mapOf("action" to "uncheck", "selected_size" to 2.5),
                mapOf("selected_size" to 2.5), mapOf("selected_size" to 2.5),
                emptyMap<String, Any>(), mapOf("deleted_size" to 1.25)), events.map { it.second })
        }
    }

    @Test fun unknownPuaAndSystemRisksNeverBecomeSafeAndIncompleteResultsAreNotExposedAsSafe() {
        val telemetry = MalwareTelemetry(sink)
        val safe = ScanFrame(apps = 5, ready = true)
        assertEquals("safe", MalwareTelemetry.resultType(safe))
        for (frame in listOf(safe.copy(unknown = 1), safe.copy(pua = 1), safe.copy(risks = listOf(DeviceRisk.USB)))) {
            telemetry.result(frame)
            assertEquals(mapOf("result_type" to "risk", "risk_count" to 1), events.last().second)
        }
        events.clear()
        for (frame in listOf(ScanFrame(), safe.copy(skipped = 1), safe.copy(limitedStorage = true), safe.copy(ready = false))) {
            telemetry.scan(frame); telemetry.result(frame)
        }
        assertEquals(4, events.size)
        assertTrue(events.all { it == MetricEvent.VIRUS_SCAN_RESULT to mapOf("result_type" to "fail") })
        telemetry.result(safe)
        assertEquals(MetricEvent.VIRUS_RESULT_SHOW to mapOf("result_type" to "safe"), events.last())
    }

    @Test fun riskActionsFollowExistingBusinessDestinationWithoutUploadingIdentifiers() {
        val telemetry = MalwareTelemetry(sink)
        fun item(level: ThreatLevel, installed: Boolean) = ThreatItem(1, "private", "private.pkg", "/private", installed, level, "private")
        telemetry.risk(null, DeviceRisk.USB)
        telemetry.risk(item(ThreatLevel.MALWARE, true), null)
        telemetry.risk(item(ThreatLevel.MALWARE, false), null)
        telemetry.risk(item(ThreatLevel.PUA, true), null)
        telemetry.risk(item(ThreatLevel.PUA, false), null)
        telemetry.risk(item(ThreatLevel.UNKNOWN, true), null)
        assertEquals(listOf("system_setting", "installed_app", "apk", "pua", "pua", "unknown"), events.map { it.second["risk_type"] })
        assertEquals(listOf("solve", "settings", "clean", "settings", "clean", "rescan"), events.map { it.second["action"] })
        assertTrue(events.all { it.second.keys == setOf("risk_type", "action") })
    }

    @Test fun scanCompletionAndRealExposureAreSeparateAndDeduplicatedAcrossCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = object : MalwareScanSource {
                override suspend fun scan(run: String, progress: (ScanFrame) -> Unit) = ScanFrame(apps = 2, ready = true)
                override suspend fun page(run: String, offset: Int) = emptyList<ThreatItem>()
                override suspend fun risks() = emptyList<DeviceRisk>()
            }
            val model = MalwareViewModel(source, MalwareTelemetry(sink))
            model.start(); runCurrent()
            model.resultVisible()
            assertEquals(listOf(MetricEvent.VIRUS_SCAN_RESULT), events.map { it.first })
            val first = model.generation
            model.revealResult(first); model.resultVisible(); model.resultVisible()
            model.refreshRisks(); runCurrent(); model.resultVisible()
            assertEquals(1, events.count { it.first == MetricEvent.VIRUS_RESULT_SHOW })
            model.userBack() // 结果页返回不算扫描页返回。
            assertEquals(0, events.count { it.first == MetricEvent.VIRUS_SCAN_BACK })
            model.scanAgain(); runCurrent()
            model.revealResult(first); model.resultVisible()
            assertEquals(1, events.count { it.first == MetricEvent.VIRUS_SCAN_AGAIN })
            assertEquals(1, events.count { it.first == MetricEvent.VIRUS_RESULT_SHOW })
            model.revealResult(model.generation); model.resultVisible()
            assertEquals(2, events.count { it.first == MetricEvent.VIRUS_RESULT_SHOW })
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedScanReportsFailOnceAndCancellationReportsNeitherSuccessNorFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var shouldFail = true
            var late: ((ScanFrame) -> Unit)? = null
            val source = object : MalwareScanSource {
                override suspend fun scan(run: String, progress: (ScanFrame) -> Unit): ScanFrame {
                    if (shouldFail) throw MalwareScanException(8)
                    late = progress
                    awaitCancellation()
                }
                override suspend fun page(run: String, offset: Int) = emptyList<ThreatItem>()
                override suspend fun risks() = emptyList<DeviceRisk>()
            }
            val model = MalwareViewModel(source, MalwareTelemetry(sink))
            model.start(); runCurrent(); model.resultVisible()
            assertEquals(listOf(MetricEvent.VIRUS_SCAN_RESULT to mapOf("result_type" to "fail")), events)
            events.clear(); shouldFail = false
            model.start(); runCurrent(); model.userBack(); model.cancel(); runCurrent()
            late!!(ScanFrame(apps = 2, ready = true)); model.resultVisible()
            assertEquals(listOf(MetricEvent.VIRUS_SCAN_BACK to emptyMap<String, Any>()), events)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun batteryScanDoesNotReportCancelledOrFailedReads() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var mode = 0
            val repository = object : BatteryRepository {
                override suspend fun read(): BatterySnapshot = when(mode) {
                    1 -> throw java.io.IOException()
                    2 -> awaitCancellation()
                    else -> BatterySnapshot(percent = 50)
                }
                override fun observe() = emptyFlow<BatterySnapshot>()
            }
            val model = BatteryEntryViewModel(repository, TimedEntryLoader(duration = { 0 }), sink)
            model.begin(); model.begin(); runCurrent()
            assertEquals(listOf(MetricEvent.BATTERYINFO_SCAN_RESULT to emptyMap<String, Any>()), events)
            model.consume(); events.clear(); mode = 1
            model.begin(); runCurrent(); mode = 2
            model.begin(); runCurrent(); model.cancel(); runCurrent()
            assertTrue(events.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
