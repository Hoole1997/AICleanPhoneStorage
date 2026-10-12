package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestFailureAction
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestTelemetry
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTestTelemetryTest {
    private val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
    private val telemetry = SpeedTestTelemetry(EventSink { event, parameters -> events += event to parameters })

    @Test fun `wire names preserve capitalization and parameterless events stay empty`() {
        telemetry.scanResult()
        telemetry.scanBack()
        telemetry.resultShown()
        telemetry.testAgain()
        assertEquals(listOf("speed_scan_result", "speed_scan_back", "speed_result_show", "TestAgain_click"),
            events.map { it.first.wireName })
        assertEquals(List(4) { emptyMap<String, Any>() }, events.map { it.second })
    }

    @Test fun `all known reasons and unknown follow documented protocol`() {
        val cases = listOf(
            SpeedTestException.Reason.NoInternet to "no_internet",
            SpeedTestException.Reason.WeakNetwork to "weak_network",
            SpeedTestException.Reason.Timeout to "timeout",
            SpeedTestException.Reason.NetworkChanged to "network_changed",
            SpeedTestException.Reason.Restricted to "network_restricted",
            SpeedTestException.Reason.NetworkInfoUnavailable to "network_info_failed",
            SpeedTestException.Reason.ServerError to "server_error",
            null to "unknown",
        )
        cases.forEach { (reason, wireReason) ->
            events.clear()
            telemetry.failureShown(reason)
            telemetry.failureAction(reason, SpeedTestFailureAction.Cancel)
            telemetry.failureAction(reason, SpeedTestFailureAction.Retry)
            assertEquals(listOf(
                MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to wireReason),
                MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to wireReason, "action" to "cancel"),
                MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to wireReason, "action" to "retry"),
            ), events)
        }
    }
}
