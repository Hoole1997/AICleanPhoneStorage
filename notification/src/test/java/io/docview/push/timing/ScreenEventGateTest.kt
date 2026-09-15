package io.docview.push.timing

import org.junit.Assert.*
import org.junit.Test

class ScreenEventGateTest {
    @Test fun twoOwnersHandleOnePhysicalUnlockOnlyOnce() {
        val gate = ScreenEventGate()
        assertTrue(gate.accept(ScreenEvent.OFF, false, true))
        assertFalse(gate.accept(ScreenEvent.OFF, false, true))
        assertTrue(gate.accept(ScreenEvent.ON, true, true))
        assertFalse(gate.accept(ScreenEvent.ON, true, true))
        assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
        assertFalse(gate.accept(ScreenEvent.UNLOCK, true, false))
    }

    @Test fun delayedEventsCannotRearmAnAlreadyHandledUnlock() {
        val gate = ScreenEventGate()
        assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
        assertFalse(gate.accept(ScreenEvent.OFF, true, false))
        assertFalse(gate.accept(ScreenEvent.ON, true, false))
        assertFalse(gate.accept(ScreenEvent.UNLOCK, true, false))
    }

    @Test fun successiveRealCyclesHaveNoDebounceTimeLimit() {
        val gate = ScreenEventGate()
        repeat(3) {
            assertTrue(gate.accept(ScreenEvent.OFF, false, true))
            assertTrue(gate.accept(ScreenEvent.ON, true, true))
            assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
        }
    }

    @Test fun lockedScreenOnRecoversAfterMissedOffWithoutInventingAnUnlock() {
        val gate = ScreenEventGate()
        assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
        assertFalse(gate.accept(ScreenEvent.ON, true, true))
        assertFalse(gate.accept(ScreenEvent.UNLOCK, true, true))
        assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
    }

    @Test fun anotherOwnerJoiningDoesNotResetTheCurrentScreenCycle() {
        val gate = ScreenEventGate()
        assertTrue(gate.accept(ScreenEvent.UNLOCK, true, false))
        // Service 注册/注销不重建 gate；存活 owner 的下一份同事件仍是重复。
        assertFalse(gate.accept(ScreenEvent.UNLOCK, true, false))
        assertFalse(gate.accept(ScreenEvent.UNLOCK, false, true))
        assertFalse(gate.accept(ScreenEvent.ON, false, true))
    }
}
