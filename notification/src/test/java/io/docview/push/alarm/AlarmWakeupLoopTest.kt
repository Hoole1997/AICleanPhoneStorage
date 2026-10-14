package io.docview.push.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmWakeupLoopTest {
    @Test fun enabledRegistersOneAlarmAndPersistedMarkerBeforeScheduling() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)

        val expected = AlarmWakeupRegistration("token-1", 1, NOW + FIFTEEN_MINUTES)
        assertEquals(expected, fixture.store.registration)
        assertEquals(listOf(expected), fixture.port.scheduled)
        assertEquals(listOf("cancel", "persist:token-1", "schedule:token-1"), fixture.events)
    }

    @Test fun coldReopenKeepsTheRegisteredAlarmEvenAfterItIsOverdue() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration
        fixture.now += 4 * FIFTEEN_MINUTES

        fixture.reopenLoop().reconcile(ENABLED)

        assertEquals(original, fixture.store.registration)
        assertEquals(1, fixture.port.scheduled.size)
    }

    @Test fun missingSystemAlarmIsRecoveredFromThePersistedMarker() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        fixture.port.present = false

        fixture.reopenLoop().reconcile(ENABLED)

        assertEquals("token-2", fixture.store.registration?.token)
        assertEquals(2, fixture.port.scheduled.size)
    }

    @Test fun aBootChangeReplacesTheOldElapsedRealtimeDeadline() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        fixture.boot = 2
        fixture.now = 20L

        fixture.reopenLoop().reconcile(ENABLED)

        assertEquals(AlarmWakeupRegistration("token-2", 2, 20L + FIFTEEN_MINUTES), fixture.store.registration)
        assertEquals(2, fixture.port.scheduled.size)
    }

    @Test fun intervalChangeDoesNotPostponeTheWaitingAlarm() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration

        fixture.loop.reconcile(AlarmWakeupPolicy(true, 60))

        assertEquals(original, fixture.store.registration)
        assertEquals(1, fixture.port.scheduled.size)
    }

    @Test fun explicitForceReplacesTheWaitingAlarm() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        fixture.now += 100L

        fixture.loop.reconcile(AlarmWakeupPolicy(true, 60), force = true)

        assertEquals(AlarmWakeupRegistration("token-2", 1, fixture.now + 60 * 60_000L), fixture.store.registration)
        assertEquals(2, fixture.port.scheduled.size)
    }

    @Test fun disabledConfigurationCancelsAndClearsExistingAlarm() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)

        fixture.loop.reconcile(DISABLED)

        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)
    }

    @Test fun staleEmptyEarlyAndWrongBootBroadcastsLeaveTheWaitingAlarmUntouched() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        val eventCount = fixture.events.size

        assertFalse(fixture.loop.beginDelivery("", ENABLED))
        assertFalse(fixture.loop.beginDelivery("previous-token", ENABLED))
        assertFalse(fixture.loop.beginDelivery(original.token, ENABLED))
        fixture.now = original.dueElapsedMillis
        fixture.boot = 2
        assertFalse(fixture.loop.beginDelivery(original.token, ENABLED))

        assertEquals(original, fixture.store.registration)
        assertTrue(fixture.port.present)
        assertEquals(eventCount, fixture.events.size)
    }

    @Test fun dueBroadcastConsumesMarkerEvenWhenTheSystemAlreadyConsumedTheAlarm() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis
        fixture.port.present = false

        assertTrue(fixture.loop.beginDelivery(original.token, ENABLED))

        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)
        assertFalse(fixture.loop.beginDelivery(original.token, ENABLED))
    }

    @Test fun disabledPolicyAtBroadcastTimePreventsDeliveryAndClearsMarker() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis

        assertFalse(fixture.loop.beginDelivery(original.token, DISABLED))

        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)
    }

    @Test fun foregroundAndForcedReconciliationCannotRegisterWhileDeliveryIsActive() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis
        assertTrue(fixture.loop.beginDelivery(original.token, ENABLED))
        val eventCount = fixture.events.size

        fixture.loop.reconcile(ENABLED)
        fixture.loop.reconcile(AlarmWakeupPolicy(true, 60), force = true)

        assertNull(fixture.store.registration)
        assertEquals(1, fixture.port.scheduled.size)
        assertEquals(eventCount, fixture.events.size)
    }

    @Test fun finishUsesLatestIntervalAndSchedulesFromCompletionWithoutCatchup() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis + 10 * FIFTEEN_MINUTES
        assertTrue(fixture.loop.beginDelivery(original.token, ENABLED))
        fixture.now += 123L

        fixture.loop.finishDelivery(AlarmWakeupPolicy(true, 60))

        assertEquals(fixture.now + 60 * 60_000L, fixture.store.registration?.dueElapsedMillis)
        assertNotEquals(original.token, fixture.store.registration?.token)
        assertEquals(2, fixture.port.scheduled.size)
        assertFalse(fixture.loop.beginDelivery(original.token, ENABLED))
    }

    @Test fun disablingWhileRunningPreventsReregistrationUntilFinished() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis
        assertTrue(fixture.loop.beginDelivery(original.token, ENABLED))

        fixture.loop.reconcile(DISABLED)
        fixture.loop.reconcile(ENABLED)
        assertEquals(1, fixture.port.scheduled.size)
        fixture.loop.finishDelivery(DISABLED)
        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)

        fixture.loop.reconcile(ENABLED)
        assertEquals(2, fixture.port.scheduled.size)
    }

    @Test fun registrationDefensivelyBoundsIntervalAndSaturatesDeadline() {
        val low = Fixture()
        low.loop.reconcile(AlarmWakeupPolicy(true, Int.MIN_VALUE))
        assertEquals(low.now + FIFTEEN_MINUTES, low.store.registration?.dueElapsedMillis)

        val high = Fixture()
        high.loop.reconcile(AlarmWakeupPolicy(true, Int.MAX_VALUE))
        assertEquals(high.now + 24 * 60 * 60_000L, high.store.registration?.dueElapsedMillis)

        val overflow = Fixture()
        overflow.now = Long.MAX_VALUE - 1
        overflow.loop.reconcile(ENABLED)
        assertEquals(Long.MAX_VALUE, overflow.store.registration?.dueElapsedMillis)
    }

    @Test fun schedulingFailureCancelsAndClearsThePersistedRegistration() {
        val fixture = Fixture()
        val failure = IllegalStateException("system schedule failed")
        fixture.port.scheduleFailure = failure

        assertSame(failure, catchFailure { fixture.loop.reconcile(ENABLED) })

        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)
        assertEquals(listOf("cancel", "persist:token-1", "schedule:token-1", "cancel", "clear"), fixture.events)
        fixture.port.scheduleFailure = null
        fixture.loop.reconcile(ENABLED)
        assertEquals("token-2", fixture.store.registration?.token)
    }

    @Test fun persistenceFailureDoesNotRegisterAnUnverifiableAlarm() {
        val fixture = Fixture()
        val failure = IllegalStateException("registration commit failed")
        fixture.store.persistFailure = failure

        assertSame(failure, catchFailure { fixture.loop.reconcile(ENABLED) })

        assertNull(fixture.store.registration)
        assertFalse(fixture.port.present)
        assertTrue(fixture.port.scheduled.isEmpty())
    }

    @Test fun cancelFailureStillClearsTheMarkerAndDoesNotSchedule() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val failure = IllegalStateException("cancel failed")
        fixture.port.cancelFailure = failure

        assertSame(failure, catchFailure { fixture.loop.reconcile(DISABLED) })

        assertNull(fixture.store.registration)
        assertEquals(1, fixture.port.scheduled.size)
        assertFalse(fixture.loop.beginDelivery("token-1", ENABLED))
    }

    @Test fun clearFailureStillCancelsThePlatformAlarmAndCanBeRecovered() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val failure = IllegalStateException("clear commit failed")
        fixture.store.clearFailure = failure

        assertSame(failure, catchFailure { fixture.loop.reconcile(DISABLED) })

        assertFalse(fixture.port.present)
        assertEquals("token-1", fixture.store.registration?.token)
        fixture.store.clearFailure = null
        fixture.loop.reconcile(ENABLED)
        assertEquals("token-2", fixture.store.registration?.token)
    }

    @Test fun failedConsumptionAbortsDeliveryAndCanRecoverOnReconciliation() {
        val fixture = Fixture()
        fixture.loop.reconcile(ENABLED)
        val original = fixture.store.registration!!
        fixture.now = original.dueElapsedMillis
        val failure = IllegalStateException("cannot consume marker")
        fixture.store.clearFailure = failure

        assertSame(failure, catchFailure { fixture.loop.beginDelivery(original.token, ENABLED) })

        assertFalse(fixture.port.present)
        fixture.store.clearFailure = null
        fixture.loop.reconcile(ENABLED)
        assertEquals("token-2", fixture.store.registration?.token)
        assertFalse(fixture.loop.beginDelivery(original.token, ENABLED))
    }

    @Test fun cleanupErrorsAreSuppressedWithoutHidingTheSchedulingFailure() {
        val fixture = Fixture()
        val failure = IllegalStateException("schedule failed")
        val cleanupFailure = IllegalStateException("clear failed")
        fixture.port.scheduleFailure = failure
        fixture.store.clearFailure = cleanupFailure

        assertSame(failure, catchFailure { fixture.loop.reconcile(ENABLED) })

        assertFalse(fixture.port.present)
        assertEquals(listOf(cleanupFailure), failure.suppressed.toList())
    }

    private fun catchFailure(block: () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("Expected failure")
    }

    private class Fixture {
        var now = NOW
        var boot = 1
        var nextToken = 0
        val events = mutableListOf<String>()
        val store = FakeStore(events)
        val port = FakePort(events, store)
        val loop = reopenLoop()

        fun reopenLoop() = AlarmWakeupLoop(store, port, { now }, { boot }, { "token-${++nextToken}" })
    }

    private class FakeStore(private val events: MutableList<String>) : AlarmWakeupStore {
        private var value: AlarmWakeupRegistration? = null
        var persistFailure: Throwable? = null
        var clearFailure: Throwable? = null

        override var registration: AlarmWakeupRegistration?
            get() = value
            set(next) {
                events += next?.let { "persist:${it.token}" } ?: "clear"
                (if (next == null) clearFailure else persistFailure)?.let { throw it }
                value = next
            }
    }

    private class FakePort(
        private val events: MutableList<String>,
        private val store: AlarmWakeupStore,
    ) : AlarmWakeupPort {
        var present = false
        var scheduleFailure: Throwable? = null
        var cancelFailure: Throwable? = null
        val scheduled = mutableListOf<AlarmWakeupRegistration>()

        override fun exists() = present

        override fun schedule(registration: AlarmWakeupRegistration) {
            events += "schedule:${registration.token}"
            assertEquals(registration, store.registration)
            // 模拟系统在注册后抛错；状态机仍须取消潜在的部分成功。
            present = true
            scheduleFailure?.let { throw it }
            scheduled += registration
        }

        override fun cancel() {
            events += "cancel"
            cancelFailure?.let { throw it }
            present = false
        }
    }

    private companion object {
        const val NOW = 10_000L
        const val FIFTEEN_MINUTES = 15 * 60_000L
        val ENABLED = AlarmWakeupPolicy(true, 15)
        val DISABLED = AlarmWakeupPolicy(false, 15)
    }
}
