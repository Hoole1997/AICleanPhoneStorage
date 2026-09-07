package com.example.aicleanphonestorage.feature.networktraffic

import com.example.aicleanphonestorage.feature.networktraffic.data.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TrafficPeriodTest {
    @Test fun `last month follows real calendar including leap day`() {
        val resolver = TrafficPeriodResolver(Clock.fixed(Instant.parse("2024-03-15T12:00:00Z"), ZoneId.of("UTC")))
        val window = resolver.resolve(TrafficPeriod.LAST_MONTH)
        assertEquals(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli(), window.startMillis)
        assertEquals(Instant.parse("2024-03-01T00:00:00Z").toEpochMilli(), window.endMillis)
    }
    @Test fun `24 hours stays 24 hours across daylight saving`() {
        val resolver = TrafficPeriodResolver(Clock.fixed(Instant.parse("2024-03-10T18:00:00Z"), ZoneId.of("America/New_York")))
        val window = resolver.resolve(TrafficPeriod.LAST_24_HOURS)
        assertEquals(86_400_000, window.endMillis - window.startMillis)
        assertEquals(Instant.parse("2024-03-01T05:00:00Z").toEpochMilli(), resolver.resolve(TrafficPeriod.THIS_MONTH).startMillis)
    }
    @Test fun `byte aggregation cannot overflow or count negative platform values`() {
        assertEquals(Long.MAX_VALUE, addBytes(Long.MAX_VALUE, 42))
        assertEquals(42, addBytes(-5, 42))
        assertNull(TrafficProgress(TrafficStage.MOBILE).percent)
        assertEquals(50, TrafficProgress(TrafficStage.APPLICATIONS, 5, 10).percent)
    }
    @Test fun `snapshot handoff is one time bounded and expires`() {
        var now = 0L
        val transfer = TrafficSnapshotTransfer { now }
        val snapshot = snapshot()
        val first = transfer.put(snapshot)
        assertNull(transfer.take(first + 1))
        assertSame(snapshot, transfer.take(first))
        assertNull(transfer.take(first))
        val expired = transfer.put(snapshot)
        now = 60_001
        assertNull(transfer.take(expired))
    }
}

internal fun snapshot(period: TrafficPeriod = TrafficPeriod.THIS_MONTH) = TrafficSnapshot(period, TrafficWindow(0, 10_000),
    NetworkUsage(100, UsageAvailability.AVAILABLE), NetworkUsage(200, UsageAvailability.AVAILABLE), emptyList())
