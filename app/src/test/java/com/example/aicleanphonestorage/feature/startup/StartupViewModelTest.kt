package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.docview.push.NotificationDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @Test fun elapsedTimeCannotReplaceAdCallbackAndDestinationIsConsumedOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            assertNull(vm.beginAd())
            advanceTimeBy(60_000); runCurrent()
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
            vm.adFinished(request)
            assertTrue(vm.state.value.ready)
            assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
            vm.adFinished(request)
            assertNull(vm.consume())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun earlyCallbackWaitsUntilThirdSecondFromPageEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.HOME)); runCurrent()
            val request = requireNotNull(vm.beginAd())
            advanceTimeBy(2_000); runCurrent()
            vm.adFinished(request); runCurrent()
            assertTrue(vm.state.value.adCompleted)
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
            advanceTimeBy(999); runCurrent()
            assertFalse(vm.state.value.ready)
            advanceTimeBy(1); runCurrent()
            assertEquals(NotificationDestination.HOME, vm.consume()?.destination)
            assertNull(vm.consume())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun instantCallbackAndNewNotificationDoNotRestartMinimumStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.CLEAN)); runCurrent()
            val request = requireNotNull(vm.beginAd())
            vm.adFinished(request); runCurrent()
            advanceTimeBy(2_500); runCurrent()
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            vm.adFinished(request)
            advanceTimeBy(500); runCurrent()
            assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun closingPageCancelsPendingMinimumStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            val store = ViewModelStore().apply { put("startup", vm) }
            runCurrent()
            vm.adFinished(requireNotNull(vm.beginAd())); runCurrent()
            store.clear()
            advanceTimeBy(10_000); runCurrent()
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun languagePreparationCompletesBeforeRequestingAd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preparation = CompletableDeferred<Unit>()
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) { preparation.await() }
            runCurrent()
            assertNull(vm.beginAd())
            preparation.complete(Unit); runCurrent()
            assertNotNull(vm.beginAd())
            assertFalse(vm.state.value.ready)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedPreparationStillRequiresAdCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) { throw java.io.IOException() }
            runCurrent()
            assertFalse(vm.state.value.ready)
            vm.adFinished(requireNotNull(vm.beginAd()))
            assertFalse(vm.state.value.ready)
            advanceTimeBy(3_000); runCurrent()
            assertTrue(vm.state.value.ready)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun incomingNotificationKeepsRequestAndUsesLatestTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.PHOTOS)); runCurrent()
            val request = requireNotNull(vm.beginAd())
            vm.accept(StartupEntry(NotificationDestination.SCREENSHOTS))
            assertNull(vm.beginAd())
            vm.adFinished(request)
            advanceTimeBy(3_000); runCurrent()
            assertEquals(NotificationDestination.SCREENSHOTS, vm.consume()?.destination)
            vm.accept(StartupEntry(NotificationDestination.CLEAN))
            val next = requireNotNull(vm.beginAd())
            vm.adFinished(request)
            assertFalse(vm.state.value.ready)
            vm.adFinished(next)
            advanceTimeBy(3_000); runCurrent()
            assertEquals(NotificationDestination.CLEAN, vm.consume()?.destination)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun processRestoreKeepsRouteButDoesNotPretendOldAdIsStillRunning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved = SavedStateHandle()
            val first = StartupViewModel(saved, clock = { testScheduler.currentTime }) {}
            first.accept(StartupEntry(NotificationDestination.UNUSED_FILES)); runCurrent()
            first.beginAd()
            val copy = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            val restored = StartupViewModel(copy, clock = { testScheduler.currentTime }) {}
            runCurrent()
            assertEquals(NotificationDestination.UNUSED_FILES, restored.entry().destination)
            assertFalse(restored.state.value.ready)
            assertNotNull(restored.beginAd())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun destroyedPageIgnoresLateCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            val store = ViewModelStore().apply { put("startup", vm) }
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            store.clear()
            vm.adFinished(request)
            assertNull(vm.consume())
            assertNull(vm.beginAd())
        } finally { Dispatchers.resetMain() }
    }
}
