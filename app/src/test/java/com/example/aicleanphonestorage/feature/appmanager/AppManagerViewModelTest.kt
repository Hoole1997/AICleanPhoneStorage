package com.example.aicleanphonestorage.feature.appmanager

import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.core.data.apps.InstalledAppSummary
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryLoader
import com.example.aicleanphonestorage.feature.appmanager.data.*
import com.example.aicleanphonestorage.feature.appmanager.ui.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private class Fake : AppManagerRepository {
        var catalog = AppManagerCatalog((1..85).map { InstalledAppSummary("app.$it", "App $it") })
        var calls = 0
        var latency = 0L
        var failed = false

        override suspend fun load(progress: (Int, Int) -> Unit): AppManagerCatalog {
            calls++
            delay(latency)
            if (failed) throw IOException("Unavailable")
            progress(catalog.apps.size, catalog.apps.size)
            return catalog
        }
    }

    private fun TestScope.entry(repo: Fake) =
        AppManagerEntryViewModel(repo, TimedEntryLoader({ 2500 }, { testScheduler.currentTime }))
            .also { store.put("entry", it) }

    @Test
    fun fastQueryWaitsForSyncedProgressAndTransfersOnlyOnce() = runTest {
        val repo = Fake()
        val vm = entry(repo)
        vm.begin()
        vm.begin()
        runCurrent()
        assertEquals(1, repo.calls)
        assertNull(vm.consume())
        assertEquals(0, (vm.state.value as AppManagerEntryState.Loading).frame?.detail?.completed)
        advanceTimeBy(1200)
        runCurrent()
        val half = (vm.state.value as AppManagerEntryState.Loading).frame!!
        assertEquals(42, half.detail.completed)
        assertEquals(49, half.percent)
        advanceTimeBy(1300)
        runCurrent()
        assertEquals(85, vm.consume()!!.apps.size)
        assertNull(vm.consume())
    }

    @Test
    fun slowQueryCannotNavigateWhenAdWindowEnds() = runTest {
        val repo = Fake().apply { latency = 5000 }
        val vm = entry(repo)
        vm.begin()
        advanceTimeBy(3000)
        runCurrent()
        assertTrue(vm.state.value is AppManagerEntryState.Loading)
        assertNull(vm.consume())
        advanceUntilIdle()
        assertNotNull(vm.consume())
    }

    @Test
    fun cancellationAndOldDialogResultsCannotCancelNewRequest() = runTest {
        val vm = entry(Fake())
        vm.begin()
        runCurrent()
        val id = (vm.state.value as AppManagerEntryState.Loading).id
        vm.cancel()
        advanceUntilIdle()
        assertNull(vm.consume())
        vm.begin()
        vm.cancel(id)
        advanceUntilIdle()
        assertNotNull(vm.consume())
    }

    @Test
    fun initialSnapshotAvoidsDuplicateQueryAndReturnUsesNewCatalog() = runTest {
        val repo = Fake()
        val initial = repo.catalog
        val vm = AppManagerViewModel(repo, initial).also { store.put("page", it) }
        vm.onForeground()
        runCurrent()
        assertEquals(0, repo.calls)
        vm.onBackground()
        repo.catalog = AppManagerCatalog(initial.apps.dropLast(1))
        repo.latency = 500
        vm.onForeground()
        runCurrent()
        assertEquals(initial, vm.state.value.catalog)
        assertTrue(vm.state.value.refreshing)
        advanceUntilIdle()
        assertEquals(84, vm.state.value.catalog!!.apps.size)
    }

    @Test
    fun failedRefreshPreservesListAndRetryWorks() = runTest {
        val repo = Fake()
        val vm = AppManagerViewModel(repo, repo.catalog).also { store.put("page", it) }
        repo.failed = true
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)
        assertEquals(85, vm.state.value.catalog!!.apps.size)
        repo.failed = false
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.state.value.failed)
    }

    @Test
    fun processRecreationLoadsWithoutEntryAdAndBackgroundCancelsRefresh() = runTest {
        val repo = Fake().apply { latency = 1000 }
        val vm = AppManagerViewModel(repo, null).also { store.put("page", it) }
        vm.onForeground()
        runCurrent()
        vm.onBackground()
        advanceUntilIdle()
        assertNull(vm.state.value.catalog)
        assertFalse(vm.state.value.refreshing)
        vm.onForeground()
        advanceUntilIdle()
        assertNotNull(vm.state.value.catalog)
    }
}
