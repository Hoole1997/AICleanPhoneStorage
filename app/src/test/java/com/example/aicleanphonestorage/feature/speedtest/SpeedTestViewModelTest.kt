package com.example.aicleanphonestorage.feature.speedtest

import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMetrics
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetwork
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestNetworkType
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestProgress
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestRepository
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestResult
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestServer
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestUiState
import com.example.aicleanphonestorage.feature.speedtest.ui.SpeedTestViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestViewModelTest {
    private val store = ViewModelStore()
    private val network = SpeedTestNetwork(SpeedTestNetworkType.Wifi, null, "Pixel")
    private val server = SpeedTestServer("Cloudflare", "HKG")
    private val result = SpeedTestResult(SpeedTestMetrics(1e6, 5e5, 20.0, 3.0, 4.0), server, "ray")
    private var runs = 0
    private var gate = CompletableDeferred<Unit>()
    private var failNext: SpeedTestException.Reason? = null

    private val repository = SpeedTestRepository {
        flow {
            runs++
            emit(SpeedTestEvent.Progress(SpeedTestProgress(SpeedTestPhase.Download, 0.5f, SpeedTestMetrics(pingMs = 20.0), server)))
            gate.await()
            failNext?.let { failNext = null; throw SpeedTestException(it) }
            emit(SpeedTestEvent.Completed(result))
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model() = SpeedTestViewModel(repository) { network }.also { store.put("speed", it) }

    @Test fun `starts on first foreground and reveals the result only after the ad callback`() = runTest {
        val vm = model()
        assertEquals(0, runs)
        vm.onForeground()
        val testing = vm.state.value as SpeedTestUiState.Testing
        assertEquals(0.5f, testing.progress.fraction)
        assertEquals(network, testing.network)
        gate.complete(Unit)
        // 成功后先等待 scan_complete_speedtest 插屏，不提前展示结果。
        assertEquals(SpeedTestUiState.Done(result, network, revealed = false), vm.state.value)
        vm.onBackground() // 插屏覆盖页面导致 onStop，不能取消已完成的结果
        vm.revealResult(vm.generation - 1) // 旧代次回调无效
        assertEquals(false, (vm.state.value as SpeedTestUiState.Done).revealed)
        vm.revealResult(vm.generation)
        assertEquals(SpeedTestUiState.Done(result, network), vm.state.value)
        vm.onForeground() // 结果页回到前台不会自动重测
        assertEquals(1, runs)
    }

    @Test fun `background cancels testing and foreground restarts from scratch`() = runTest {
        val vm = model()
        vm.onForeground()
        vm.onBackground()
        assertEquals(0f, (vm.state.value as SpeedTestUiState.Testing).progress.fraction)
        vm.onForeground()
        assertEquals(2, runs)
        gate.complete(Unit)
        assertTrue(vm.state.value is SpeedTestUiState.Done)
    }

    @Test fun `ad callback from a previous run cannot reveal a new test`() = runTest {
        val vm = model()
        gate.complete(Unit)
        vm.onForeground()
        val first = vm.generation
        vm.start() // Test Again 前的旧回调迟到
        vm.revealResult(first)
        assertEquals(false, (vm.state.value as SpeedTestUiState.Done).revealed)
    }

    @Test fun `failure keeps previous result for cancel and closes when there is none`() = runTest {
        val vm = model()
        failNext = SpeedTestException.Reason.NoInternet
        gate.complete(Unit)
        vm.onForeground()
        val failed = vm.state.value as SpeedTestUiState.Failed
        assertEquals(SpeedTestException.Reason.NoInternet, failed.reason)
        assertTrue("no previous result: page should close", vm.dismissFailure())

        vm.start()
        vm.revealResult(vm.generation)
        assertTrue(vm.state.value is SpeedTestUiState.Done)
        failNext = SpeedTestException.Reason.Failed
        vm.start()
        assertTrue(vm.state.value is SpeedTestUiState.Failed)
        assertFalse("previous result restored", vm.dismissFailure())
        assertEquals(SpeedTestUiState.Done(result, network), vm.state.value)
    }
}
