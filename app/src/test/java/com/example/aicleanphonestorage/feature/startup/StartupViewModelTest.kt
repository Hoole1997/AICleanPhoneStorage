package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import io.docview.push.NotificationDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @Test fun readyStartupUsesShortTransitionAndConsumesDestinationOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), {}, { testScheduler.currentTime })
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            vm.resumed(); runCurrent()
            advanceTimeBy(2_999); runCurrent()
            assertFalse(vm.state.value.ready)
            advanceTimeBy(1); runCurrent()
            assertTrue(vm.state.value.ready)
            assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
            assertNull(vm.consume())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun stuckPreparationCannotHoldStartupBeyondFifteenSeconds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var cancelled = false
            val vm = StartupViewModel(SavedStateHandle(), {
                try { awaitCancellation() } finally { cancelled = true }
            }, { testScheduler.currentTime })
            vm.accept(StartupEntry(NotificationDestination.CLEAN))
            vm.resumed(); runCurrent()
            advanceTimeBy(14_999); runCurrent()
            assertFalse(vm.state.value.ready)
            advanceTimeBy(1); runCurrent()
            assertTrue(vm.state.value.ready)
            assertTrue(cancelled)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun backgroundCancelsWorkAndExpiredResumeDoesNotRestartWait() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = StartupViewModel(SavedStateHandle(), { calls++; awaitCancellation() }, { testScheduler.currentTime })
            vm.accept(StartupEntry(NotificationDestination.HOME))
            vm.resumed(); runCurrent()
            advanceTimeBy(1_000); vm.paused(); runCurrent()
            advanceTimeBy(60_000); runCurrent()
            assertFalse(vm.state.value.ready)
            vm.resumed(); runCurrent()
            assertTrue(vm.state.value.ready)
            assertEquals(1, calls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun restoredSessionAndNewNotificationKeepDeadlineAndLatestTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved = SavedStateHandle()
            val first = StartupViewModel(saved, {}, { testScheduler.currentTime })
            first.accept(StartupEntry(NotificationDestination.PHOTOS))
            first.resumed(); runCurrent(); advanceTimeBy(2_000); first.paused(); runCurrent()
            val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            val next = StartupViewModel(restored, {}, { testScheduler.currentTime })
            assertEquals(NotificationDestination.PHOTOS, next.entry().destination)
            next.accept(StartupEntry(NotificationDestination.SCREENSHOTS))
            next.resumed(); runCurrent(); advanceTimeBy(1_000); runCurrent()
            assertEquals(NotificationDestination.SCREENSHOTS, next.consume()?.destination)
            assertEquals(3_000, testScheduler.currentTime)
        } finally { Dispatchers.resetMain() }
    }
}
