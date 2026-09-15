package com.example.aicleanphonestorage.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.docview.push.NotificationDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupOfflineTest {
    private fun test(block: TestScope.(StartupViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
        val store = ViewModelStore().apply { put("startup", vm) }
        try {
            vm.accept(StartupEntry(NotificationDestination.SCREENSHOTS))
            vm.permissionFinished()
            vm.visibilityChanged(true)
            runCurrent()
            block(vm)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun offlineThreeSecondsRequiresRequestCleanupThenNavigatesOnlyOnce() = test { vm ->
        vm.networkChanged(StartupNetworkState.OFFLINE)
        val id = requireNotNull(vm.beginAd())
        runCurrent()
        advanceTimeBy(2_999); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        advanceTimeBy(1); runCurrent()
        assertEquals(id, vm.state.value.offlineTimeout)
        assertFalse(vm.isAdRequestActive(id))
        assertNull(vm.consume())
        vm.adFinished(id) // 迟到 SDK 回调不能抢在清理前跳转。
        assertNull(vm.consume())
        vm.offlineWaitReleased(id)
        assertEquals(NotificationDestination.SCREENSHOTS, vm.consume()?.destination)
        vm.adFinished(id)
        assertNull(vm.consume())
    }

    @Test fun reconnectCancelsCountdownAndNextDisconnectStartsNewThreeSeconds() = test { vm ->
        vm.networkChanged(StartupNetworkState.OFFLINE)
        vm.beginAd(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        vm.networkChanged(StartupNetworkState.ONLINE)
        advanceTimeBy(10_000); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        vm.networkChanged(StartupNetworkState.OFFLINE); runCurrent()
        advanceTimeBy(2_999); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        advanceTimeBy(1); runCurrent()
        assertNotNull(vm.state.value.offlineTimeout)
    }

    @Test fun loadedAdAndNormalCallbackAreNotInterruptedByOfflineTimer() = test { vm ->
        vm.networkChanged(StartupNetworkState.OFFLINE)
        val id = requireNotNull(vm.beginAd()); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        vm.adContentLoaded(id)
        advanceTimeBy(10_000); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        assertFalse(vm.state.value.ready)
        vm.adFinished(id)
        assertNotNull(vm.consume())
    }

    @Test fun hiddenPageStopsTimerAndReturnRequiresFreshOfflineObservation() = test { vm ->
        vm.networkChanged(StartupNetworkState.OFFLINE)
        vm.beginAd(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        vm.visibilityChanged(false)
        advanceTimeBy(20_000); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        vm.visibilityChanged(true)
        advanceTimeBy(5_000); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        vm.networkChanged(StartupNetworkState.OFFLINE); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertNotNull(vm.state.value.offlineTimeout)
    }

    @Test fun unknownConnectivityNeverReplacesSdkCallback() = test { vm ->
        vm.networkChanged(StartupNetworkState.UNKNOWN)
        vm.beginAd(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertNull(vm.state.value.offlineTimeout)
        assertFalse(vm.state.value.ready)
    }

    @Test fun notificationDuringOfflineWaitKeepsLatestRouteAndNewVisitDoesNotReuseOldTimeout() = test { vm ->
        vm.networkChanged(StartupNetworkState.OFFLINE)
        val id = requireNotNull(vm.beginAd()); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        vm.accept(StartupEntry(NotificationDestination.NETWORK))
        advanceTimeBy(1_000); runCurrent()
        vm.offlineWaitReleased(id)
        assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
        vm.accept(StartupEntry(NotificationDestination.HOME, hotStart = true))
        vm.networkChanged(StartupNetworkState.ONLINE)
        val next = requireNotNull(vm.beginAd())
        vm.offlineWaitReleased(id)
        vm.adFinished(id)
        assertFalse(vm.state.value.ready)
        vm.adFinished(next)
        advanceTimeBy(3_000); runCurrent()
        assertTrue(requireNotNull(vm.consume()).hotStart)
    }
}
