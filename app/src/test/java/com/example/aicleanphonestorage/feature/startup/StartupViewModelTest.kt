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
    @Test
    fun hotEntrySurvivesSavedStateRestoreAndNotificationReplacesReturnMode() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved = SavedStateHandle()
            val vm = StartupViewModel(saved, clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.HOME, hotStart = true))
            val restored = StartupViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }), clock = { testScheduler.currentTime }) {}
            assertTrue(restored.entry().hotStart)
            restored.permissionFinished()
            runCurrent()
            val request = requireNotNull(restored.beginAd())
            restored.adFinished(request)
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(requireNotNull(restored.consume()).hotStart)
            assertNull(restored.consume())
            restored.accept(StartupEntry(NotificationDestination.PHOTOS))
            assertFalse(restored.entry().hotStart)
            assertEquals(NotificationDestination.PHOTOS, restored.entry().destination)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun elapsedTimeCannotReplaceAdCallbackAndDestinationIsConsumedOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            assertNull(vm.beginAd())
            advanceTimeBy(60_000)
            runCurrent()
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
            vm.adFinished(request)
            assertTrue(vm.state.value.ready)
            assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
            vm.adFinished(request)
            assertNull(vm.consume())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun earlyCallbackWaitsUntilThirdSecondFromAdRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            vm.accept(StartupEntry(NotificationDestination.HOME))
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            advanceTimeBy(2_000)
            runCurrent()
            vm.adFinished(request)
            runCurrent()
            assertTrue(vm.state.value.adCompleted)
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
            advanceTimeBy(999)
            runCurrent()
            assertFalse(vm.state.value.ready)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(NotificationDestination.HOME, vm.consume()?.destination)
            assertNull(vm.consume())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun instantCallbackAndNewNotificationDoNotRestartMinimumStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            vm.accept(StartupEntry(NotificationDestination.CLEAN))
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            vm.adFinished(request)
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            vm.adFinished(request)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(NotificationDestination.NETWORK, vm.consume()?.destination)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun closingPageCancelsPendingMinimumStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            val store = ViewModelStore().apply { put("startup", vm) }
            runCurrent()
            vm.adFinished(requireNotNull(vm.beginAd()))
            runCurrent()
            store.clear()
            advanceTimeBy(10_000)
            runCurrent()
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun languagePreparationCompletesBeforeRequestingAd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preparation = CompletableDeferred<Unit>()
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {
                        preparation.await()
                    }
                    .also { it.permissionFinished() }
            runCurrent()
            assertNull(vm.beginAd())
            preparation.complete(Unit)
            runCurrent()
            assertNotNull(vm.beginAd())
            assertFalse(vm.state.value.ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedPreparationStillRequiresAdCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {
                        throw java.io.IOException()
                    }
                    .also { it.permissionFinished() }
            runCurrent()
            assertFalse(vm.state.value.ready)
            vm.adFinished(requireNotNull(vm.beginAd()))
            assertFalse(vm.state.value.ready)
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(vm.state.value.ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun incomingNotificationKeepsRequestAndUsesLatestTarget() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            vm.accept(StartupEntry(NotificationDestination.PHOTOS))
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            vm.accept(StartupEntry(NotificationDestination.SCREENSHOTS))
            assertNull(vm.beginAd())
            vm.adFinished(request)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(NotificationDestination.SCREENSHOTS, vm.consume()?.destination)
            vm.accept(StartupEntry(NotificationDestination.CLEAN))
            val next = requireNotNull(vm.beginAd())
            vm.adFinished(request)
            assertFalse(vm.state.value.ready)
            vm.adFinished(next)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(NotificationDestination.CLEAN, vm.consume()?.destination)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun processRestoreKeepsRouteButDoesNotPretendOldAdIsStillRunning() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved = SavedStateHandle()
            val first =
                StartupViewModel(saved, clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            first.accept(StartupEntry(NotificationDestination.UNUSED_FILES))
            runCurrent()
            first.beginAd()
            val copy = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            val restored =
                StartupViewModel(copy, clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            runCurrent()
            assertEquals(NotificationDestination.UNUSED_FILES, restored.entry().destination)
            assertFalse(restored.state.value.ready)
            assertNotNull(restored.beginAd())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun destroyedPageIgnoresLateCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm =
                StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
                    .also { it.permissionFinished() }
            val store = ViewModelStore().apply { put("startup", vm) }
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            store.clear()
            vm.adFinished(request)
            assertNull(vm.consume())
            assertNull(vm.beginAd())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun permissionFlowMustFinishBeforeAnyAdRequestEvenAfterMinimumStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertNull(vm.beginAd())
            assertNull(vm.consume())
            vm.accept(StartupEntry(NotificationDestination.LARGE_FILES))
            assertNull(vm.beginAd())
            vm.permissionFinished()
            val id = requireNotNull(vm.beginAd())
            assertNull(vm.beginAd())
            vm.adFinished(id)
            assertNull(vm.consume())
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(NotificationDestination.LARGE_FILES, vm.consume()?.destination)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun longPermissionSettingsVisitCannotConsumeMinimumVisibleStartupStay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }) {}
            vm.accept(StartupEntry(NotificationDestination.NETWORK))
            runCurrent()
            // 模拟多次拒绝和系统设置页内停留；此时广告尚未请求。
            advanceTimeBy(45_000)
            runCurrent()
            vm.permissionFinished()
            // 授权结果先到达，窗口晚一点恢复焦点；计时必须等可见页面真正开始广告请求。
            advanceTimeBy(7_000)
            runCurrent()
            val request = requireNotNull(vm.beginAd())
            vm.adFinished(request)
            runCurrent()
            assertTrue(vm.state.value.adCompleted)
            assertFalse(vm.state.value.ready)
            assertNull(vm.consume())
            advanceTimeBy(2_999)
            runCurrent()
            vm.permissionFinished()
            vm.accept(StartupEntry(NotificationDestination.LARGE_FILES))
            assertNull(vm.beginAd())
            assertNull(vm.consume())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(NotificationDestination.LARGE_FILES, vm.consume()?.destination)
            assertNull(vm.consume())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
