package com.example.aicleanphonestorage.core.permissions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    private class Access : PermissionAccess {
        val enabled = mutableSetOf<PermissionKind>()
        var checks = 0
        var persisted: String? = null
        var slow = false

        override suspend fun granted(kind: PermissionKind): Boolean {
            checks++
            if (slow) withContext(NonCancellable) { delay(1000) }
            return kind in enabled
        }

        override suspend fun persistDirectory(uri: String) {
            persisted = uri
        }
    }

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(
        access: Access,
        saved: SavedStateHandle = SavedStateHandle(),
        timeout: Long = 3000,
    ) =
        PermissionFlowViewModel(access, saved, { testScheduler.currentTime }, timeout).also {
            store.put("permissions", it)
        }

    private fun TestScope.launch(
        vm: PermissionFlowViewModel,
        kind: PermissionKind,
        settings: Boolean = kind.special,
    ): String {
        vm.begin("test", kind, settings)
        runCurrent()
        val id = vm.state.value!!.id
        assertEquals(PermissionPhase.LAUNCH, vm.state.value!!.phase)
        assertTrue(vm.launched(id))
        vm.leftHost()
        runCurrent()
        return id
    }

    @Test
    fun detectsGrantWhileAwayAndReturnsOnlyOnce() = runTest {
        val access = Access()
        val vm = vm(access)
        val id = launch(vm, PermissionKind.USAGE)
        access.enabled += PermissionKind.USAGE
        advanceTimeBy(750)
        runCurrent()
        assertEquals(PermissionPhase.RESULT, vm.state.value!!.phase)
        assertTrue(vm.state.value!!.granted)
        assertTrue(vm.markReturnAttempted(id))
        assertFalse(vm.markReturnAttempted(id))
        val count = access.checks
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(count, access.checks)
        assertNotNull(vm.consume())
        assertNull(vm.consume())
        assertFalse(vm.pending)
    }

    @Test
    fun timeoutStopsPollingAndManualReturnCanStillContinue() = runTest {
        val access = Access()
        val vm = vm(access, timeout = 1500)
        launch(vm, PermissionKind.ALL_FILES)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(PermissionPhase.EXPIRED, vm.state.value!!.phase)
        val checks = access.checks
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(checks, access.checks)
        access.enabled += PermissionKind.ALL_FILES
        vm.resumed()
        runCurrent()
        assertTrue(vm.consume()!!.granted)
    }

    @Test
    fun cancellingDisposesLoopAndIgnoresOldRequest() = runTest {
        val access = Access()
        val vm = vm(access)
        val old = launch(vm, PermissionKind.USAGE)
        vm.cancel()
        val count = access.checks
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(count, access.checks)
        vm.begin("new", PermissionKind.NOTIFICATIONS)
        runCurrent()
        vm.launchFailed(old)
        assertEquals("new", vm.state.value!!.route)
        assertEquals(PermissionPhase.LAUNCH, vm.state.value!!.phase)
    }

    @Test
    fun runtimeSheetUsesResultWithoutBackgroundPolling() = runTest {
        val access = Access()
        val vm = vm(access)
        launch(vm, PermissionKind.PHOTOS)
        assertTrue(vm.requestedRuntime(PermissionKind.PHOTOS))
        val count = access.checks
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(count, access.checks)
        access.enabled += PermissionKind.PHOTOS
        vm.runtimeResult()
        runCurrent()
        assertTrue(vm.consume()!!.granted)
    }

    @Test
    fun staleRuntimeResultCannotConsumeAnotherPermission() = runTest {
        val access = Access()
        val vm = vm(access)
        launch(vm, PermissionKind.PHOTOS)
        vm.cancel()
        vm.begin("new", PermissionKind.USAGE)
        runCurrent()
        vm.runtimeResult()
        runCurrent()
        assertEquals(PermissionPhase.LAUNCH, vm.state.value!!.phase)
    }

    @Test
    fun directoryOnlySucceedsWithReturnedPersistedUri() = runTest {
        val access = Access()
        val vm = vm(access)
        launch(vm, PermissionKind.DIRECTORY)
        vm.directoryResult("content://test/tree/folder")
        runCurrent()
        val result = vm.consume()!!
        assertTrue(result.granted)
        assertEquals(access.persisted, result.directory)
    }

    @Test
    fun restorationRetainsDeadlineAndReturnAttempt() = runTest {
        val access = Access()
        val saved = SavedStateHandle()
        val first = vm(access, saved)
        val id = launch(first, PermissionKind.USAGE)
        access.enabled += PermissionKind.USAGE
        advanceTimeBy(750)
        runCurrent()
        first.markReturnAttempted(id)
        store.clear()
        val restored = vm(access, saved)
        runCurrent()
        assertEquals(id, restored.state.value!!.id)
        assertFalse(restored.markReturnAttempted(id))
        assertTrue(restored.consume()!!.granted)
    }

    @Test
    fun cancelledBlockingCheckCannotOverwriteNextRequest() = runTest {
        val access = Access().apply { slow = true }
        val vm = vm(access)
        vm.begin("old", PermissionKind.USAGE)
        runCurrent()
        vm.cancel()
        access.slow = false
        vm.begin("new", PermissionKind.ALL_FILES)
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("new", vm.state.value!!.route)
        assertEquals(PermissionPhase.LAUNCH, vm.state.value!!.phase)
    }

    @Test
    fun failedNativeLaunchReleasesContractForRetry() = runTest {
        val vm = vm(Access())
        val id = launch(vm, PermissionKind.PHOTOS)
        vm.launchFailed(id)
        assertTrue(vm.consume()!!.unavailable)
        launch(vm, PermissionKind.PHOTOS)
        assertEquals(PermissionPhase.WAITING, vm.state.value!!.phase)
    }

    @Test
    fun returningWithoutGrantStopsWatcherAndReportsDenial() = runTest {
        val access = Access()
        val vm = vm(access)
        launch(vm, PermissionKind.NOTIFICATIONS)
        vm.resumed()
        runCurrent()
        assertFalse(vm.consume()!!.granted)
        val count = access.checks
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(count, access.checks)
    }

    @Test
    fun alreadyGrantedSkipsSystemPageAndPolling() = runTest {
        val access = Access().apply { enabled += PermissionKind.NOTIFICATIONS }
        val vm = vm(access)
        vm.begin("notification", PermissionKind.NOTIFICATIONS)
        runCurrent()
        assertEquals(PermissionPhase.RESULT, vm.state.value!!.phase)
        assertTrue(vm.consume()!!.granted)
        assertEquals(1, access.checks)
    }

    @Test
    fun notificationSettingsDetectGrantWhileAwayAndReturnOnce() = runTest {
        val access = Access()
        val vm = vm(access)
        val id = launch(vm, PermissionKind.POST_NOTIFICATIONS, settings = true)
        assertTrue(vm.state.value!!.settings)
        assertFalse(vm.requestedRuntime(PermissionKind.POST_NOTIFICATIONS))
        access.enabled += PermissionKind.POST_NOTIFICATIONS
        advanceTimeBy(750)
        runCurrent()
        assertTrue(vm.state.value!!.granted)
        assertTrue(vm.state.value!!.leftHost)
        assertTrue(vm.markReturnAttempted(id))
        assertFalse(vm.markReturnAttempted(id))
        val checks = access.checks
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(checks, access.checks)
        assertTrue(vm.consume()!!.granted)
        assertFalse(vm.pending)
    }

    @Test
    fun notificationSettingsStopOnTimeoutAndOnManualReturnWithoutGrant() = runTest {
        val access = Access()
        val vm = vm(access, timeout = 1500)
        launch(vm, PermissionKind.POST_NOTIFICATIONS, settings = true)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(PermissionPhase.EXPIRED, vm.state.value!!.phase)
        val timedOutChecks = access.checks
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(timedOutChecks, access.checks)
        vm.resumed()
        runCurrent()
        assertFalse(vm.consume()!!.granted)
        launch(vm, PermissionKind.POST_NOTIFICATIONS, settings = true)
        vm.resumed()
        runCurrent()
        assertFalse(vm.consume()!!.granted)
        val returnedChecks = access.checks
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(returnedChecks, access.checks)
    }

    @Test
    fun notificationRuntimeDialogDoesNotStartSettingsPolling() = runTest {
        val access = Access()
        val vm = vm(access)
        launch(vm, PermissionKind.POST_NOTIFICATIONS, settings = false)
        val checks = access.checks
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(checks, access.checks)
        access.enabled += PermissionKind.POST_NOTIFICATIONS
        vm.runtimeResult()
        runCurrent()
        assertTrue(vm.consume()!!.granted)
    }
}
