package io.docview.push.boot

import org.junit.Assert.*
import org.junit.Test

class BootRestorePolicyTest {
    private fun reason(opened: Boolean = true, enabled: Boolean = true, permission: Boolean = true,
                       restricted: Boolean = false, running: Boolean = false, boot: Int = 20, last: Int = 19) =
        BootRestorePolicy.skipReason(opened, enabled, permission, restricted, running, boot, last)
    @Test fun eligibleBootIsAllowed() { assertNull(reason()) }
    @Test fun neverOpenedCannotRestore() { assertNotNull(reason(opened = false)) }
    @Test fun disabledCannotRestore() { assertNotNull(reason(enabled = false)) }
    @Test fun deniedNotificationCannotRestore() { assertNotNull(reason(permission = false)) }
    @Test fun systemRestrictionCannotRestore() { assertNotNull(reason(restricted = true)) }
    @Test fun runningServiceIsNotStartedAgain() { assertNotNull(reason(running = true)) }
    @Test fun duplicateBootDoesNotRetryEvenAfterFailedAttempt() { assertNotNull(reason(last = 20)) }
    @Test fun unknownBootDoesNotRestore() { assertNotNull(reason(boot = -1)) }
    @Test fun nextBootCanAttemptAgain() { assertNull(reason(boot = 21, last = 20)) }
    @Test fun bootAttemptBlocksIncidentalRetriesUntilForeground() {
        BootRestoreSession.begin()
        assertFalse(BootRestoreSession.allowNormalStart(background = true))
        assertFalse(BootRestoreSession.allowNormalStart(background = true))
        assertTrue(BootRestoreSession.allowNormalStart(background = false))
        assertTrue(BootRestoreSession.allowNormalStart(background = true))
    }
}
