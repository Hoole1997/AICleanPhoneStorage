package com.example.aicleanphonestorage.feature.home

import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository
import com.example.aicleanphonestorage.feature.home.data.ScanSummary
import com.example.aicleanphonestorage.feature.home.ui.HomeUiState
import com.example.aicleanphonestorage.feature.home.ui.HomeViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(block: () -> Flow<HomeOverview>): HomeViewModel =
        HomeViewModel(object : HomeOverviewRepository {
            override fun observeOverview() = block()
        }).also { store.put("home", it) }

    private fun TestScope.observe(viewModel: HomeViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    @Test
    fun `repository stops in background while retaining last overview for smooth return`() = runTest {
        var active = 0
        var starts = 0
        val vm = viewModel {
            flow {
                active++
                starts++
                try {
                    emit(HomeOverview())
                    awaitCancellation()
                } finally {
                    active--
                }
            }
        }
        runCurrent()
        assertEquals(0, starts)
        val first = observe(vm)
        val second = observe(vm)
        runCurrent()
        assertEquals(1, starts)
        first.cancelAndJoin()
        runCurrent()
        assertEquals(1, active)
        second.cancelAndJoin()
        runCurrent()
        assertEquals(0, active)
        assertEquals(HomeUiState.Ready(HomeOverview()), vm.uiState.value)
        observe(vm)
        runCurrent()
        assertEquals(2, starts)
        assertEquals(1, active)
        store.clear()
        runCurrent()
        assertEquals(0, active)
    }

    @Test
    fun `failed read can retry and show a completed scan with zero junk`() = runTest {
        var reads = 0
        val result = HomeOverview(scan = ScanSummary.Completed(0, 1))
        val vm = viewModel {
            flow {
                reads++
                if (reads == 1) throw IOException("storage unavailable")
                emit(result)
            }
        }
        observe(vm)
        runCurrent()
        assertEquals(HomeUiState.Failure(HomeUiState.Reason.StorageUnavailable), vm.uiState.value)
        vm.retry()
        runCurrent()
        assertEquals(HomeUiState.Ready(result), vm.uiState.value)
        vm.retry()
        runCurrent()
        assertEquals(2, reads)
    }

    @Test
    fun `revoked permission is explicit instead of returning empty storage`() = runTest {
        val vm = viewModel { flow { throw SecurityException("permission revoked") } }
        observe(vm)
        runCurrent()
        assertEquals(HomeUiState.Failure(HomeUiState.Reason.PermissionRequired), vm.uiState.value)
    }
}
