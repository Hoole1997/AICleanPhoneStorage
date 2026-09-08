package com.example.aicleanphonestorage.feature.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryLoader
import com.example.aicleanphonestorage.feature.notifications.data.*
import com.example.aicleanphonestorage.feature.notifications.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCleanerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private class Fake : NotificationAppsRepository {
        override val selectedPackages = MutableStateFlow<Set<String>>(emptySet())
        var granted = true
        var loads = 0
        var saveFails = false
        val catalog = NotificationCatalog((1..85).map { NotificationApp("app.$it", "App $it") })
        override suspend fun hasAccess() = granted
        override suspend fun loadApps(progress: (Int, Int) -> Unit): NotificationCatalog { loads++; progress(85, 85); return catalog }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            delay(20)
            if (saveFails) throw IOException("disk unavailable")
            selectedPackages.value = if (enabled) selectedPackages.value + packageName else selectedPackages.value - packageName
        }
    }
    private fun TestScope.vm(repo: Fake, entry: Boolean) = NotificationCleanerViewModel(repo, flowOf(true), SavedStateHandle(), entry,
        if (entry) null else repo.catalog, TimedEntryLoader({2500}, {testScheduler.currentTime})).also { store.put("notification", it) }

    @Test fun `entry waits for permission and uses shared progress timeline`() = runTest {
        val repo = Fake().apply { granted = false }
        val vm = vm(repo, true)
        vm.onForeground(); runCurrent(); assertEquals(0, repo.loads)
        vm.beginEntry(); runCurrent(); assertEquals(NotificationPhase.NeedsAccess, vm.state.value.phase)
        vm.awaitAccess(); vm.onBackground(); repo.granted = true; vm.onForeground(); runCurrent()
        assertEquals(0, (vm.state.value.phase as NotificationPhase.Loading).frame?.detail?.completed)
        advanceTimeBy(1200); runCurrent()
        assertEquals(42, (vm.state.value.phase as NotificationPhase.Loading).frame?.detail?.completed)
        assertNull(vm.consumeCatalog())
        advanceTimeBy(1300); runCurrent()
        assertNotNull(vm.consumeCatalog()); assertNull(vm.consumeCatalog())
    }
    @Test fun `cancelled entry cannot navigate after the ad display window`() = runTest {
        val repo = Fake(); val vm = vm(repo, true)
        vm.beginEntry(); runCurrent(); vm.cancelEntry(); advanceUntilIdle()
        assertEquals(NotificationPhase.Idle, vm.state.value.phase)
        assertNull(vm.consumeCatalog())
    }
    @Test fun `switch saves without reloading catalog and failure restores stored selection`() = runTest {
        val repo = Fake(); val vm = vm(repo, false)
        vm.onForeground(); runCurrent()
        vm.setEnabled("app.1", true); runCurrent()
        assertEquals(true, vm.state.value.saving["app.1"])
        advanceTimeBy(20); runCurrent()
        assertTrue("app.1" in vm.state.value.selected)
        assertEquals(0, repo.loads)
        repo.saveFails = true
        vm.setEnabled("app.1", false); advanceTimeBy(20); runCurrent()
        assertTrue("app.1" in vm.state.value.selected)
        assertTrue(vm.state.value.saving.isEmpty())
        assertEquals(1L, vm.state.value.saveError)
    }
}
