package com.example.aicleanphonestorage.feature.push

import androidx.lifecycle.SavedStateHandle
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import org.junit.Assert.*
import org.junit.Test

class PushPermissionTelemetryTest {
    private class Fixture(val saved: SavedStateHandle = SavedStateHandle()) {
        val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val sink = EventSink { event, params -> events += event to params }
        val tracker = PushPermissionTelemetry(saved)
    }

    @Test fun alreadyAllowedReportsOnlyAllow1OnceAcrossRepeatedChecksAndRecreation() {
        for (position in PushPermissionPosition.entries) {
            val f = Fixture()
            repeat(3) { f.tracker.alreadyGranted(position, f.sink) }
            PushPermissionTelemetry(f.saved).alreadyGranted(position, f.sink)
            assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_RESULT to mapOf("Notific_Allow_Position" to position.wire, "Result" to "allow1")), f.events)
            f.tracker.reset()
            f.tracker.alreadyGranted(position, f.sink)
            assertEquals(2, f.events.size)
        }
    }

    @Test fun realRequestHasOneStartAndExactResultValuesWithTheOriginalPosition() {
        for (position in PushPermissionPosition.entries) for (outcome in listOf(PushPermissionOutcome.ALLOWED, PushPermissionOutcome.DENIED, PushPermissionOutcome.DENIED_FOREVER)) {
            val f = Fixture()
            val token = f.tracker.started(position, PushPermissionRequestMode.RUNTIME, f.sink)
            assertEquals(token, f.tracker.started(position, PushPermissionRequestMode.RUNTIME, f.sink))
            f.tracker.alreadyGranted(position, f.sink) // 允许后的 onResume 可能先于 SDK 回调到达。
            assertTrue(f.tracker.completed(token, outcome, f.sink))
            assertFalse(f.tracker.completed(token, outcome, f.sink))
            f.tracker.alreadyGranted(position, f.sink)
            assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_START, MetricEvent.NOTIFICATION_ALLOW_RESULT), f.events.map { it.first })
            assertEquals(mapOf("Notific_Allow_Position" to position.wire), f.events.first().second)
            assertEquals(mapOf("Notific_Allow_Position" to position.wire, "Result" to outcome.wire), f.events.last().second)
        }
        assertEquals("deined_forever", PushPermissionOutcome.DENIED_FOREVER.wire)
    }

    @Test fun abandonedAndFailedRequestsDoNotInventPermissionResults() {
        val f = Fixture()
        val old = f.tracker.started(PushPermissionPosition.SPLASH, PushPermissionRequestMode.RUNTIME, f.sink)
        val restored = PushPermissionTelemetry(f.saved)
        assertFalse(restored.completed(old, PushPermissionOutcome.DENIED, f.sink))
        restored.alreadyGranted(PushPermissionPosition.SPLASH, f.sink)
        assertEquals(1, f.events.size)
        restored.reset()
        val next = restored.started(PushPermissionPosition.HOME, PushPermissionRequestMode.RUNTIME, f.sink)
        assertNotEquals(old, next)
        assertFalse(restored.completed(old, PushPermissionOutcome.ALLOWED, f.sink))
        assertTrue(restored.completed(next, PushPermissionOutcome.UNAVAILABLE, f.sink))
        assertEquals(2, f.events.size) // 两个真实 Start，没有捏造拒绝或默认允许。
    }

    @Test fun pendingSettingsResultSurvivesProcessRecreationWithoutAnotherStart() {
        val f = Fixture()
        val token = f.tracker.started(PushPermissionPosition.HOME, PushPermissionRequestMode.SETTINGS, f.sink)
        val restored = PushPermissionTelemetry(f.saved)
        assertEquals(token, restored.active(PushPermissionRequestMode.SETTINGS))
        assertTrue(restored.completed(token, PushPermissionOutcome.ALLOWED, f.sink))
        assertEquals(2, f.events.size)
        assertEquals("allow", f.events.last().second["Result"])
    }
}
