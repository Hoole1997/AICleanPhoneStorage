package com.example.aicleanphonestorage.feature.speedtest

import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestFailureAction
import com.example.aicleanphonestorage.feature.speedtest.analytics.SpeedTestTelemetry
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
    private val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
    private val telemetry = SpeedTestTelemetry(EventSink { event, parameters -> events += event to parameters })

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

    private fun model(source: SpeedTestRepository = repository) = SpeedTestViewModel(source, telemetry) { network }
        .also { store.put("speed", it) }

    private fun eventCount(event: MetricEvent) = events.count { it.first == event }

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
        assertEquals(1, eventCount(MetricEvent.SPEED_SCAN_RESULT))
        vm.resultVisible() // 插屏之前渲染 100% 扫描状态，不是结果曝光。
        assertEquals(0, eventCount(MetricEvent.SPEED_RESULT_SHOW))
        vm.onBackground() // 插屏覆盖页面导致 onStop，不能取消已完成的结果
        vm.revealResult(vm.generation - 1) // 旧代次回调无效
        assertEquals(false, (vm.state.value as SpeedTestUiState.Done).revealed)
        vm.revealResult(vm.generation)
        assertEquals(SpeedTestUiState.Done(result, network), vm.state.value)
        assertEquals(0, eventCount(MetricEvent.SPEED_RESULT_SHOW)) // 回调不等同实际前台渲染。
        vm.resultVisible()
        vm.resultVisible()
        vm.revealResult(vm.generation)
        assertEquals(1, eventCount(MetricEvent.SPEED_RESULT_SHOW))
        vm.onForeground() // 结果页回到前台不会自动重测
        vm.resultVisible()
        assertEquals(1, runs)
        assertEquals(1, eventCount(MetricEvent.SPEED_RESULT_SHOW))
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
        assertEquals(1, eventCount(MetricEvent.SPEED_SCAN_RESULT)) // 取消的上一轮没有成功事件。
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

    @Test fun `cancel exits home even after a previous complete result and reports once`() = runTest {
        val vm = model()
        failNext = SpeedTestException.Reason.NoInternet
        gate.complete(Unit)
        vm.onForeground()
        val failed = vm.state.value as SpeedTestUiState.Failed
        assertEquals(SpeedTestException.Reason.NoInternet, failed.reason)
        vm.failureVisible(vm.generation, failed.reason)
        vm.failureVisible(vm.generation, failed.reason)
        assertTrue("no previous result: page should close", vm.failureAction(vm.generation, failed.reason, SpeedTestFailureAction.Cancel))
        assertFalse(vm.failureAction(vm.generation, failed.reason, SpeedTestFailureAction.Cancel))
        assertEquals(listOf(
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "no_internet"),
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "no_internet", "action" to "cancel"),
        ), events)

        vm.start()
        vm.revealResult(vm.generation)
        assertTrue(vm.state.value is SpeedTestUiState.Done)
        failNext = SpeedTestException.Reason.ServerError
        vm.start()
        assertTrue(vm.state.value is SpeedTestUiState.Failed)
        assertTrue("Feishu Cancel returns home even after Test Again", vm.failureAction(vm.generation,
            SpeedTestException.Reason.ServerError, SpeedTestFailureAction.Cancel))
        assertTrue(vm.state.value is SpeedTestUiState.Failed)
    }

    @Test fun `retry and restored dialog retain original reason and reject stale action`() = runTest {
        val vm = model()
        failNext = SpeedTestException.Reason.Timeout
        gate.complete(Unit)
        vm.onForeground()
        val firstGeneration = vm.generation
        vm.failureVisible(firstGeneration, SpeedTestException.Reason.Timeout)
        assertFalse(vm.failureAction(firstGeneration - 1, SpeedTestException.Reason.Timeout, SpeedTestFailureAction.Retry))
        assertFalse(vm.failureAction(firstGeneration, SpeedTestException.Reason.ServerError, SpeedTestFailureAction.Cancel))
        assertEquals(1, runs)
        failNext = SpeedTestException.Reason.NetworkChanged
        assertFalse(vm.failureAction(firstGeneration, SpeedTestException.Reason.Timeout, SpeedTestFailureAction.Retry))
        assertEquals(2, runs)
        vm.failureVisible(vm.generation, SpeedTestException.Reason.NetworkChanged)
        vm.failureVisible(vm.generation, SpeedTestException.Reason.NetworkChanged)
        assertFalse(vm.failureAction(firstGeneration, SpeedTestException.Reason.Timeout, SpeedTestFailureAction.Cancel))
        assertEquals(listOf(
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "timeout"),
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "timeout", "action" to "retry"),
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "network_changed"),
        ), events)
        assertEquals(0, eventCount(MetricEvent.TEST_AGAIN_CLICK))
    }

    @Test fun `Test Again is only a visible result button click and each result shows once`() = runTest {
        val vm = model()
        vm.onForeground()
        vm.testAgain()
        assertEquals(1, runs)
        gate.complete(Unit)
        vm.testAgain() // 仍等待插屏，不应触发按钮事件或重新测速。
        assertEquals(1, runs)
        vm.revealResult(vm.generation)
        vm.resultVisible()
        vm.testAgain()
        assertEquals(2, runs)
        assertEquals(1, eventCount(MetricEvent.TEST_AGAIN_CLICK))
        assertEquals(2, eventCount(MetricEvent.SPEED_SCAN_RESULT))
        assertEquals(1, eventCount(MetricEvent.SPEED_RESULT_SHOW))
        vm.revealResult(vm.generation)
        vm.resultVisible()
        assertEquals(2, eventCount(MetricEvent.SPEED_RESULT_SHOW))
    }

    @Test fun `scan back stops connections and failure cancel or result back is not scan back`() = runTest {
        val vm = model()
        vm.onForeground()
        vm.scanBackRequested()
        vm.scanBackRequested()
        gate.complete(Unit)
        assertEquals(1, eventCount(MetricEvent.SPEED_SCAN_BACK))
        assertEquals(0, eventCount(MetricEvent.SPEED_SCAN_RESULT))
        vm.start()
        vm.revealResult(vm.generation)
        vm.scanBackRequested()
        assertEquals(1, eventCount(MetricEvent.SPEED_SCAN_BACK))
        failNext = SpeedTestException.Reason.WeakNetwork
        vm.start()
        vm.scanBackRequested()
        assertEquals(1, eventCount(MetricEvent.SPEED_SCAN_BACK))
    }

    @Test fun `completed event followed by error never reports scan success`() = runTest {
        val vm = model(SpeedTestRepository { flow {
            emit(SpeedTestEvent.Completed(result))
            throw SpeedTestException(SpeedTestException.Reason.ServerError)
        } })
        vm.onForeground()
        assertTrue(vm.state.value is SpeedTestUiState.Failed)
        assertEquals(0, eventCount(MetricEvent.SPEED_SCAN_RESULT))
        assertEquals(0, eventCount(MetricEvent.SPEED_SCAN_FAIL)) // 仅完成前台弹框显示才曝光。
    }

    @Test fun `late non cooperative error after background cancellation never becomes failure`() = runTest {
        val vm = model(SpeedTestRepository { flow {
            withContext(NonCancellable) { gate.await() }
            throw SpeedTestException(SpeedTestException.Reason.Timeout)
        } })
        vm.onForeground()
        vm.onBackground()
        gate.complete(Unit)
        assertTrue(vm.state.value is SpeedTestUiState.Testing)
        assertTrue(events.isEmpty())
    }

    @Test fun `unexpected exception preserves unknown telemetry reason`() = runTest {
        val vm = model(SpeedTestRepository { flow { throw IllegalStateException("unexpected repository state") } })
        vm.onForeground()
        val failed = vm.state.value as SpeedTestUiState.Failed
        assertEquals(null, failed.reason)
        vm.failureVisible(vm.generation, failed.reason)
        vm.failureAction(vm.generation, failed.reason, SpeedTestFailureAction.Cancel)
        assertEquals(listOf(
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "unknown"),
            MetricEvent.SPEED_SCAN_FAIL to mapOf("reason" to "unknown", "action" to "cancel"),
        ), events)
    }
}
