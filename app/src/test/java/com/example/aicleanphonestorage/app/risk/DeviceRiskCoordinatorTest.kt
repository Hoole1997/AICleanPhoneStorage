package com.example.aicleanphonestorage.app.risk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRiskCoordinatorTest {
    private val snapshot = DeviceRiskSnapshot(setOf(DeviceRiskSignal.VPN, DeviceRiskSignal.NO_GP))

    @Test fun `initial waiters resume only after policy application finishes`() = runTest {
        val policyReady = CompletableDeferred<Unit>()
        var applying = false
        var applied: DeviceRiskSnapshot? = null
        var released = 0
        val statuses = mutableListOf<Pair<DeviceRiskStatus, DeviceRiskUnavailableReason?>>()
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = { snapshot }, apply = {
            applying = true
            policyReady.await()
            applied = it
        }, release = { released++ }, reportStatus = { status, reason -> statuses += status to reason })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        assertTrue(applying)
        assertFalse(waiter.isCompleted)
        assertEquals(DeviceRiskStatus.DETECTING, coordinator.state.value)
        policyReady.complete(Unit)
        runCurrent()
        waiter.await()
        assertEquals(snapshot, applied)
        assertEquals(DeviceRiskStatus.APPLIED, coordinator.state.value)
        assertEquals(listOf(DeviceRiskStatus.APPLIED to null), statuses)
        assertEquals(0, released)
    }

    @Test fun `slow detection has one absolute deadline and releases waiters once`() = runTest {
        var applications = 0
        var releases = 0
        val statuses = mutableListOf<Pair<DeviceRiskStatus, DeviceRiskUnavailableReason?>>()
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = {
            delay(16_000)
            snapshot
        }, apply = { applications++ }, release = { releases++ },
            reportStatus = { status, reason -> statuses += status to reason })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        advanceTimeBy(14_999); runCurrent()
        assertFalse(waiter.isCompleted)
        assertEquals(DeviceRiskStatus.DETECTING, coordinator.state.value)
        advanceTimeBy(1); runCurrent()
        waiter.await()
        assertEquals(DeviceRiskStatus.TIMED_OUT, coordinator.state.value)
        assertEquals(listOf(DeviceRiskStatus.TIMED_OUT to DeviceRiskUnavailableReason.TIMEOUT), statuses)
        advanceTimeBy(60_000); runCurrent()
        coordinator.start()
        assertEquals(0, applications)
        assertEquals(1, releases)
        assertEquals(1, statuses.size)
    }

    @Test fun `policy synchronization uses the remaining detection deadline`() = runTest {
        var applying = false
        var releases = 0
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = {
            delay(10_000)
            snapshot
        }, apply = {
            applying = true
            awaitCancellation()
        }, release = { releases++ })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertTrue(applying)
        advanceTimeBy(4_999); runCurrent()
        assertFalse(waiter.isCompleted)
        advanceTimeBy(1); runCurrent()
        waiter.await()
        assertEquals(DeviceRiskStatus.TIMED_OUT, coordinator.state.value)
        assertEquals(1, releases)
    }

    @Test fun `SDK failures and native linkage failures release without applying partial signals`() = runTest {
        val failures = listOf(
            DeviceRiskUnavailableException(DeviceRiskUnavailableReason.FINGERPRINT) to DeviceRiskUnavailableReason.FINGERPRINT,
            IllegalStateException("SDK failure") to DeviceRiskUnavailableReason.SDK_ERROR,
            UnsatisfiedLinkError("missing native ABI") to DeviceRiskUnavailableReason.SDK_ERROR,
        )
        for ((failure, expectedReason) in failures) {
            var applications = 0
            var releases = 0
            val statuses = mutableListOf<Pair<DeviceRiskStatus, DeviceRiskUnavailableReason?>>()
            val coordinator = DeviceRiskCoordinator(backgroundScope, detect = { throw failure },
                apply = { applications++ }, release = { releases++ },
                reportStatus = { status, reason -> statuses += status to reason })
            val waiter = async { coordinator.awaitInitialDecision() }
            runCurrent()
            waiter.await()
            assertEquals(DeviceRiskStatus.FAILED_OPEN, coordinator.state.value)
            assertEquals(listOf(DeviceRiskStatus.FAILED_OPEN to expectedReason), statuses)
            assertEquals(0, applications)
            assertEquals(1, releases)
        }
    }

    @Test fun `failed policy application releases any partially applied restrictions`() = runTest {
        var applying = 0
        var releases = 0
        val statuses = mutableListOf<Pair<DeviceRiskStatus, DeviceRiskUnavailableReason?>>()
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = { snapshot }, apply = {
            applying++
            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.POLICY)
        }, release = { releases++ }, reportStatus = { status, reason -> statuses += status to reason })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        waiter.await()
        assertEquals(1, applying)
        assertEquals(1, releases)
        assertEquals(DeviceRiskStatus.FAILED_OPEN, coordinator.state.value)
        assertEquals(listOf(DeviceRiskStatus.FAILED_OPEN to DeviceRiskUnavailableReason.POLICY), statuses)
    }

    @Test fun `a SDK that swallows deadline cancellation cannot apply its late result`() = runTest {
        var lateReturns = 0
        var applications = 0
        var releases = 0
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                lateReturns++
                snapshot
            }
        }, apply = { applications++ }, release = { releases++ })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        advanceTimeBy(15_000); runCurrent()
        waiter.await()
        assertEquals(1, lateReturns)
        assertEquals(0, applications)
        assertEquals(1, releases)
        assertEquals(DeviceRiskStatus.TIMED_OUT, coordinator.state.value)
    }

    @Test fun `cancelling one page waiter does not cancel application detection or other waiters`() = runTest {
        var detections = 0
        var applications = 0
        var releases = 0
        val coordinator = DeviceRiskCoordinator(backgroundScope, detect = {
            detections++
            delay(2_000)
            snapshot
        }, apply = { applications++ }, release = { releases++ })
        val firstPage = async { coordinator.awaitInitialDecision() }
        val secondPage = async { coordinator.awaitInitialDecision() }
        coordinator.start()
        coordinator.start()
        runCurrent()
        assertEquals(1, detections)
        firstPage.cancelAndJoin()
        assertFalse(secondPage.isCompleted)
        assertEquals(DeviceRiskStatus.DETECTING, coordinator.state.value)
        advanceTimeBy(2_000); runCurrent()
        secondPage.await()
        coordinator.awaitInitialDecision()
        assertEquals(1, detections)
        assertEquals(1, applications)
        assertEquals(0, releases)
        assertEquals(DeviceRiskStatus.APPLIED, coordinator.state.value)
    }

    @Test fun `application cancellation releases pending and never started decisions`() = runTest {
        for (cancelBeforeStart in listOf(false, true)) {
            val applicationJob = SupervisorJob(backgroundScope.coroutineContext[Job])
            val applicationScope = CoroutineScope(backgroundScope.coroutineContext + applicationJob)
            var applications = 0
            var releases = 0
            val statuses = mutableListOf<Pair<DeviceRiskStatus, DeviceRiskUnavailableReason?>>()
            val coordinator = DeviceRiskCoordinator(applicationScope, detect = { awaitCancellation() },
                apply = { applications++ }, release = { releases++ },
                reportStatus = { status, reason -> statuses += status to reason })
            if (cancelBeforeStart) applicationScope.cancel()
            val waiter = async { coordinator.awaitInitialDecision() }
            runCurrent()
            if (!cancelBeforeStart) applicationScope.cancel()
            runCurrent()
            waiter.await()
            assertEquals(DeviceRiskStatus.FAILED_OPEN, coordinator.state.value)
            assertEquals(listOf(DeviceRiskStatus.FAILED_OPEN to DeviceRiskUnavailableReason.CANCELLED), statuses)
            assertEquals(0, applications)
            assertEquals(1, releases)
        }
    }

    @Test fun `status reporting failure cannot prevent a completed fail open decision`() = runTest {
        var releases = 0
        val coordinator = DeviceRiskCoordinator(backgroundScope,
            detect = { throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR) },
            apply = { error("unavailable result must not apply") }, release = { releases++ },
            reportStatus = { _, _ -> error("reporter failed") })
        val waiter = async { coordinator.awaitInitialDecision() }
        runCurrent()
        waiter.await()
        assertEquals(DeviceRiskStatus.FAILED_OPEN, coordinator.state.value)
        assertEquals(1, releases)
    }
}
