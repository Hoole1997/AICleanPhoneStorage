package io.docview.push.service

import org.junit.Assert.*
import org.junit.Test

class CoreServiceLifecycleTest {
    private class Fixture {
        val calls = mutableListOf<String>()
        var promoteError: Exception? = null
        var workError: Exception? = null
        var cancelError: Exception? = null
        var leaveError: Exception? = null
        var reportError: Exception? = null
        val session = CoreServiceLifecycle(
            promote = { calls += "promote"; promoteError?.let { throw it } },
            beginWork = { calls += "begin"; workError?.let { throw it } },
            cancelWork = { calls += "cancel"; cancelError?.let { throw it } },
            leaveForeground = { calls += "leave"; leaveError?.let { throw it } },
            stopService = { calls += "stopSelf" },
            reportFailure = { calls += "report"; reportError?.let { throw it } },
        )
    }

    @Test fun onlyNullIntentIsStickyRestoreAndKnownActionsKeepTheirMeaning() {
        assertEquals(CoreServiceCommand.RESTORE, CoreServiceCommand.from(true, null))
        assertEquals(CoreServiceCommand.UNKNOWN, CoreServiceCommand.from(false, null))
        assertEquals(CoreServiceCommand.UNKNOWN, CoreServiceCommand.from(false, "invalid"))
        assertEquals(CoreServiceCommand.START, CoreServiceCommand.from(false, CoreServiceCommand.ACTION_START))
        assertEquals(CoreServiceCommand.STOP, CoreServiceCommand.from(false, CoreServiceCommand.ACTION_STOP))
        assertEquals(CoreServiceCommand.UPDATE, CoreServiceCommand.from(false, CoreServiceCommand.ACTION_UPDATE))
    }

    @Test fun newServiceInstanceRestoresForegroundBeforeWorkAndDoesNotShareRunningState() {
        val first = Fixture()
        assertTrue(first.session.start(true))
        val restored = Fixture()
        assertTrue(restored.session.start(true))
        assertEquals(listOf("promote", "begin"), restored.calls)
        assertTrue(restored.session.running)
    }

    @Test fun repeatedStartRefreshesForegroundWithoutCreatingDuplicateTasks() {
        val f = Fixture()
        repeat(3) { assertTrue(f.session.start(true)) }
        assertEquals(3, f.calls.count { it == "promote" })
        assertEquals(1, f.calls.count { it == "begin" })
    }

    @Test fun promotionFailureStopsEvenBeforeRunningFlagWasSet() {
        for (error in listOf(SecurityException("permission"), IllegalStateException("background denied"), IllegalArgumentException("bad notification"))) {
            val f = Fixture().apply { promoteError = error }
            assertFalse(f.session.start(true))
            assertFalse(f.session.running)
            assertEquals(listOf("promote", "cancel", "leave", "stopSelf", "report"), f.calls)
            assertFalse(f.session.start(true))
            assertFalse(f.calls.contains("begin"))
        }
    }

    @Test fun taskInitializationFailureCleansUpAfterPromotion() {
        val f = Fixture().apply { workError = IllegalStateException("initialization failed") }
        assertFalse(f.session.start(true))
        assertEquals(listOf("promote", "begin", "cancel", "leave", "stopSelf", "report"), f.calls)
        assertFalse(f.session.running)
    }

    @Test fun refreshFailureStopsRunningTasksAndCannotBeRestartedByLateCallbacks() {
        val f = Fixture()
        f.session.start(true)
        f.promoteError = SecurityException("revoked")
        assertFalse(f.session.refresh())
        assertFalse(f.session.running)
        assertFalse(f.session.start(true))
        assertEquals(1, f.calls.count { it == "begin" })
        assertEquals(1, f.calls.count { it == "stopSelf" })
    }

    @Test fun disabledRestoreAndUpdateWithoutAStartExitInsteadOfLeavingIdleService() {
        val disabled = Fixture()
        assertFalse(disabled.session.start(false))
        assertEquals(listOf("cancel", "leave", "stopSelf"), disabled.calls)
        val update = Fixture()
        assertFalse(update.session.refresh())
        assertEquals(listOf("cancel", "leave", "stopSelf"), update.calls)
    }

    @Test fun foregroundHandshakePrecedesDisabledShutdownWithoutStartingWork() {
        val f = Fixture()
        assertTrue(f.session.prepareForeground())
        assertFalse(f.session.running)
        assertFalse(f.session.start(false))
        assertEquals(listOf("promote", "cancel", "leave", "stopSelf"), f.calls)
    }

    @Test fun bootstrapFailureCannotContinueIntoWorkOrRetryInTheSameInstance() {
        val f = Fixture().apply { promoteError = IllegalArgumentException("bootstrap") }
        assertFalse(f.session.prepareForeground())
        assertFalse(f.session.start(true))
        assertFalse(f.calls.contains("begin"))
        assertEquals(1, f.calls.count { it == "stopSelf" })
    }

    @Test fun timeoutOrStopIsIdempotentAndCleanupFailuresNeverPreventStopSelf() {
        val f = Fixture()
        f.session.start(true)
        f.cancelError = IllegalStateException("cancel")
        f.leaveError = IllegalStateException("leave")
        f.reportError = IllegalStateException("reporter")
        f.session.stop()
        f.session.stop()
        assertFalse(f.session.running)
        assertEquals(1, f.calls.count { it == "cancel" })
        assertEquals(1, f.calls.count { it == "leave" })
        assertEquals(1, f.calls.count { it == "stopSelf" })
    }
}
