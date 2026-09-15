package com.example.aicleanphonestorage.feature.networktraffic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.feature.networktraffic.data.*
import com.example.aicleanphonestorage.feature.networktraffic.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTrafficViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private class Fake : NetworkTrafficRepository {
        var granted = true
        var calls = 0
        var loader: suspend (TrafficPeriod, (TrafficProgress) -> Unit) -> TrafficSnapshot = { period, _ -> snapshot(period) }
        override suspend fun hasUsageAccess() = granted
        override suspend fun load(period: TrafficPeriod, onProgress: (TrafficProgress) -> Unit): TrafficSnapshot { calls++; return loader(period, onProgress) }
    }
    private fun vm(repo: Fake, entry: Boolean = false, initial: TrafficSnapshot? = null) = NetworkTrafficViewModel(repo, SavedStateHandle(), { 10_000L }, entry, initial, minimumEntryLoadingMillis = { 0 }).also { store.put("traffic", it) }

    @Test fun `entry count percentage and navigation share one display timeline`() = runTest {
        val repo = Fake().apply {
            loader = { period, progress ->
                progress(TrafficProgress(TrafficStage.APPLICATIONS, 85, 85))
                snapshot(period).copy(apps = (1..85).map { TrafficApp(it, emptyList(), 1, 1) })
            }
        }
        val vm = NetworkTrafficViewModel(repo, SavedStateHandle(), entryMode = true,
            minimumEntryLoadingMillis = { 3000 }, monotonicMillis = { testScheduler.currentTime }).also { store.put("traffic", it) }
        vm.beginEntry(); runCurrent()
        val initial = vm.state.value.status as TrafficStatus.Loading
        assertEquals(0, initial.progress.completed)
        assertEquals(0, initial.displayPercent)
        advanceTimeBy(1450); runCurrent()
        val middle = vm.state.value.status as TrafficStatus.Loading
        assertEquals(42, middle.progress.completed)
        assertEquals(49, middle.displayPercent)
        assertNull(vm.consumeEntrySnapshot())
        advanceTimeBy(1450); runCurrent()
        val lastFrame = vm.state.value.status as TrafficStatus.Loading
        assertEquals(85, lastFrame.progress.completed)
        assertEquals(100, lastFrame.displayPercent)
        advanceTimeBy(100); runCurrent()
        assertEquals(TrafficStatus.Ready, vm.state.value.status)
    }

    @Test fun `fast entry waits for ad window but can be cancelled without navigation`() = runTest {
        val repo = Fake()
        val vm = NetworkTrafficViewModel(repo, SavedStateHandle(), entryMode = true,
            minimumEntryLoadingMillis = { 2500 }, monotonicMillis = { testScheduler.currentTime }).also { store.put("traffic", it) }
        vm.beginEntry(); runCurrent()
        assertTrue(vm.state.value.status is TrafficStatus.Loading)
        advanceTimeBy(2499); runCurrent()
        assertNull(vm.consumeEntrySnapshot())
        vm.cancelEntry(); advanceUntilIdle()
        assertEquals(TrafficStatus.Idle, vm.state.value.status)
        assertNull(vm.consumeEntrySnapshot())
    }

    @Test fun `entry waits only remaining duration and date changes have no ad delay`() = runTest {
        val repo = Fake().apply { loader = { period, _ -> delay(1000); snapshot(period) } }
        val vm = NetworkTrafficViewModel(repo, SavedStateHandle(), entryMode = true,
            minimumEntryLoadingMillis = { 2500 }, monotonicMillis = { testScheduler.currentTime }).also { store.put("traffic", it) }
        vm.beginEntry(); runCurrent()
        advanceTimeBy(2500); runCurrent()
        assertEquals(TrafficStatus.Ready, vm.state.value.status)
        assertNotNull(vm.consumeEntrySnapshot())
        val page = NetworkTrafficViewModel(repo, SavedStateHandle(), entryMode = false,
            minimumEntryLoadingMillis = { error("Page refresh must not request an ad delay") }).also { store.put("page", it) }
        page.selectPeriod(TrafficPeriod.THIS_WEEK)
        advanceTimeBy(1000); runCurrent()
        assertEquals(TrafficStatus.Ready, page.state.value.status)
    }

    @Test fun `slow query has no additional ad wait`() = runTest {
        val repo = Fake().apply { loader = { period, _ -> delay(5000); snapshot(period) } }
        val vm = NetworkTrafficViewModel(repo, SavedStateHandle(), entryMode = true,
            minimumEntryLoadingMillis = { 2500 }, monotonicMillis = { testScheduler.currentTime }).also { store.put("traffic", it) }
        vm.beginEntry(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(TrafficStatus.Ready, vm.state.value.status)
    }

    @Test fun `home does not query before click and navigates only after success once`() = runTest {
        val repo = Fake()
        val gate = CompletableDeferred<Unit>()
        repo.loader = { period, _ -> gate.await(); snapshot(period) }
        val vm = vm(repo, entry = true)
        vm.onForeground(); runCurrent()
        assertEquals(0, repo.calls)
        vm.beginEntry(); runCurrent()
        assertTrue(vm.state.value.status is TrafficStatus.Loading)
        assertNull(vm.consumeEntrySnapshot())
        vm.beginEntry(); runCurrent()
        assertEquals(1, repo.calls)
        gate.complete(Unit); runCurrent()
        assertNotNull(vm.consumeEntrySnapshot())
        assertNull(vm.consumeEntrySnapshot())
        vm.onForeground(); runCurrent()
        assertEquals(TrafficStatus.Idle, vm.state.value.status)
        assertEquals(1, repo.calls)
    }
    @Test fun `permission must be granted before loading and opening`() = runTest {
        val repo = Fake().apply { granted = false }
        val vm = vm(repo, entry = true)
        vm.beginEntry(); runCurrent()
        assertEquals(TrafficStatus.NeedsAccess, vm.state.value.status)
        assertEquals(0, repo.calls)
        vm.awaitPermission(); vm.onBackground()
        repo.granted = true
        vm.onForeground(); runCurrent()
        assertEquals(TrafficStatus.Ready, vm.state.value.status)
        assertNotNull(vm.consumeEntrySnapshot())
    }
    @Test fun `cancelled entry drops even a non cooperative late result`() = runTest {
        val repo = Fake()
        repo.loader = { period, report -> withContext(NonCancellable) { delay(100); report(TrafficProgress(TrafficStage.APPLICATIONS, 1, 1)); snapshot(period) } }
        val vm = vm(repo, entry = true)
        vm.beginEntry(); runCurrent()
        vm.cancelEntry(); advanceUntilIdle()
        assertEquals(TrafficStatus.Idle, vm.state.value.status)
        assertNull(vm.consumeEntrySnapshot())
    }
    @Test fun `destination uses transferred snapshot without second query`() = runTest {
        val repo = Fake()
        val vm = vm(repo, initial = snapshot())
        vm.onForeground(); runCurrent()
        assertEquals(0, repo.calls)
        assertEquals(TrafficStatus.Ready, vm.state.value.status)
    }
    @Test fun `this week refreshes stale data when returning to foreground`() = runTest {
        val repo = Fake()
        val vm = NetworkTrafficViewModel(repo, SavedStateHandle(), now = { 50_001 }, initialSnapshot = snapshot(TrafficPeriod.THIS_WEEK))
            .also { store.put("traffic", it) }
        vm.onForeground(); advanceUntilIdle()
        assertEquals(1, repo.calls)
        assertEquals(TrafficPeriod.THIS_WEEK, vm.state.value.snapshot?.period)
    }
    @Test fun `latest date wins and old dialog cancellation cannot cancel new date`() = runTest {
        val repo = Fake()
        repo.loader = { period, _ -> delay(if (period == TrafficPeriod.THIS_WEEK) 1000 else 100); snapshot(period) }
        val vm = vm(repo, initial = snapshot())
        vm.selectPeriod(TrafficPeriod.THIS_WEEK); runCurrent()
        val oldId = (vm.state.value.status as TrafficStatus.Loading).requestId
        vm.selectPeriod(TrafficPeriod.LAST_24_HOURS)
        assertFalse(vm.cancelByUser(oldId))
        advanceUntilIdle()
        assertEquals(TrafficPeriod.LAST_24_HOURS, vm.state.value.snapshot?.period)
    }
    @Test fun `timeout is recoverable and background cancels query`() = runTest {
        val repo = Fake().apply { loader = { _, _ -> awaitCancellation() } }
        val vm = vm(repo)
        vm.onForeground(); runCurrent()
        advanceTimeBy(15_001); runCurrent()
        assertEquals(TrafficStatus.Failed(timeout = true), vm.state.value.status)
        vm.selectPeriod(TrafficPeriod.THIS_MONTH); runCurrent()
        vm.onBackground(); runCurrent()
        assertEquals(TrafficStatus.Paused, vm.state.value.status)
    }
}
