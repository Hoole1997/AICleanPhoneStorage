package com.example.aicleanphonestorage.feature.settings.addebug

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.example.aicleanphonestorage.app.ad.AdInitializationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** 不初始化 Application 或真实广告 SDK，验证调试配置和预览互斥的用户可见约束。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdDebugViewModelTest {
    @get:Rule
    val mainDispatcher = AdDebugMainDispatcherRule()

    private val models = ViewModelStore()
    private var modelNumber = 0
    private val sdkState = MutableStateFlow(AdInitializationState.READY)
    private val messages = AdDebugMessages(
        operationFailed = "Settings operation failed",
        cancelled = "Preview cancelled",
        restored = "Defaults restored",
    )

    @After
    fun releaseModels() {
        // 取消每个模型的 StateFlow 收集和未完成设置任务，不能让测试泄漏 viewModelScope。
        models.clear()
    }

    @Test
    fun `initial read and SDK readiness both gate preview without replacing stored controls`() =
        runTest(mainDispatcher.dispatcher) {
            sdkState.value = AdInitializationState.INITIALIZING
            val readGate = CompletableDeferred<Unit>()
            val settings = FakeAdDebugSettings(AdDebugControls(AdSource.GAM, false)).apply {
                controlsReadGate = readGate
            }
            val model = model(settings)

            assertTrue(model.state.value.loading)
            assertFalse(model.beginShow())
            runCurrent()
            assertTrue(model.state.value.loading)

            readGate.complete(Unit)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(AdSource.GAM, model.state.value.source)
            assertFalse(model.state.value.globalEnabled)
            assertFalse(model.state.value.sdkReady)
            assertFalse(model.beginShow())

            sdkState.value = AdInitializationState.READY
            runCurrent()
            assertTrue(model.state.value.sdkReady)
            assertEquals(AdSource.GAM, model.state.value.source)
            assertFalse(model.state.value.globalEnabled)
            assertFalse(model.beginShow())
            model.setGlobalEnabled(true)
            runCurrent()
            assertTrue(model.beginShow())
        }

    @Test
    fun `active preview rejects double taps and all configuration changes`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings()
            val model = model(settings)
            runCurrent()
            val diagnosticQueriesBeforeShow = settings.diagnosticQueries.size

            assertTrue(model.beginShow())
            assertFalse(model.beginShow())
            model.selectSource(AdSource.ADMOB)
            model.selectFormat(DebugAdFormat.BANNER)
            model.setGlobalEnabled(false)
            model.resetDefaults()
            model.refresh()
            runCurrent()

            assertTrue(model.state.value.busy)
            assertEquals(AdSource.BIDDING, model.state.value.source)
            assertEquals(DebugAdFormat.INTERSTITIAL, model.state.value.format)
            assertTrue(model.state.value.globalEnabled)
            assertTrue(settings.mutations.isEmpty())
            assertEquals(diagnosticQueriesBeforeShow, settings.diagnosticQueries.size)
        }

    @Test
    fun `pending settings mutation rejects overlapping edits until its readback finishes`() =
        runTest(mainDispatcher.dispatcher) {
            val mutationGate = CompletableDeferred<Unit>()
            val settings = FakeAdDebugSettings()
            val model = model(settings)
            runCurrent()
            settings.sourceWriteGate = mutationGate

            model.selectSource(AdSource.TOPON)
            assertTrue(model.state.value.loading)
            assertFalse(model.beginShow())
            runCurrent()
            assertEquals(1, settings.activeCalls)

            model.selectSource(AdSource.GAM)
            model.selectFormat(DebugAdFormat.NATIVE)
            model.setGlobalEnabled(false)
            model.resetDefaults()
            model.refresh()
            runCurrent()
            assertEquals(listOf("source:TOPON"), settings.mutations)

            mutationGate.complete(Unit)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(AdSource.TOPON, model.state.value.source)
            assertEquals(DebugAdFormat.INTERSTITIAL, model.state.value.format)
            assertTrue(model.state.value.globalEnabled)
            assertEquals(1, settings.maxConcurrentCalls)
            assertEquals(0, settings.activeCalls)
        }

    @Test
    fun `failed initial read reports an error and a later refresh can recover`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings(AdDebugControls(AdSource.PANGLE, false)).apply {
                readFailure = IllegalStateException("Private SDK details must not be displayed")
            }
            val model = model(settings)
            runCurrent()

            assertFalse(model.state.value.loading)
            assertTrue(model.state.value.result.startsWith(messages.operationFailed))
            assertFalse(model.state.value.result.contains("Private SDK"))
            assertTrue(model.state.value.diagnostics.contains(messages.operationFailed))
            assertFalse(model.state.value.diagnostics.contains("Private SDK"))

            settings.readFailure = null
            model.refresh()
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(AdSource.PANGLE, model.state.value.source)
            assertFalse(model.state.value.globalEnabled)
            assertTrue(model.state.value.diagnostics.contains(settings.diagnosticText))
        }

    @Test
    fun `failed source write does not report a new source and releases the edit gate`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings()
            val model = model(settings)
            runCurrent()
            settings.sourceWriteFailure = IllegalArgumentException("secret")

            model.selectSource(AdSource.MAX)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(AdSource.BIDDING, model.state.value.source)
            assertEquals(AdSource.BIDDING, settings.controls.source)
            assertTrue(model.state.value.result.startsWith(messages.operationFailed))
            assertFalse(model.state.value.result.contains("secret"))

            settings.sourceWriteFailure = null
            model.selectSource(AdSource.MAX)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(AdSource.MAX, model.state.value.source)
            assertTrue(model.beginShow())
        }

    @Test
    fun `diagnostic failure is visible and retry reads actual persisted controls`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings()
            val model = model(settings)
            runCurrent()
            settings.diagnosticFailure = IllegalStateException("query failed")

            model.setGlobalEnabled(false)
            runCurrent()
            assertFalse(settings.controls.globalEnabled)
            assertFalse(model.state.value.loading)
            assertTrue(model.state.value.result.startsWith(messages.operationFailed))

            settings.diagnosticFailure = null
            model.refresh()
            runCurrent()
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.globalEnabled)
            assertTrue(model.state.value.diagnostics.contains(settings.diagnosticText))
        }

    @Test
    fun `selected valid format survives recreation and drives subsequent diagnostics`() =
        runTest(mainDispatcher.dispatcher) {
            val saved = SavedStateHandle(mapOf(FORMAT_KEY to DebugAdFormat.BANNER.name))
            val settings = FakeAdDebugSettings()
            val original = model(settings, saved)
            runCurrent()
            assertEquals(DebugAdFormat.BANNER, original.state.value.format)

            original.selectFormat(DebugAdFormat.FULL_SCREEN_NATIVE)
            runCurrent()
            val restored = model(settings, SavedStateHandle(mapOf(FORMAT_KEY to saved.get<String>(FORMAT_KEY))))
            runCurrent()

            assertEquals(DebugAdFormat.FULL_SCREEN_NATIVE, restored.state.value.format)
            assertEquals(DebugAdFormat.FULL_SCREEN_NATIVE, settings.diagnosticQueries.last().first)
        }

    @Test
    fun `unknown saved format falls back to an available preview format`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings()
            val model = model(settings, SavedStateHandle(mapOf(FORMAT_KEY to "REMOVED_SDK_FORMAT")))
            runCurrent()

            assertEquals(DebugAdFormat.INTERSTITIAL, model.state.value.format)
            assertEquals(DebugAdFormat.INTERSTITIAL, settings.diagnosticQueries.last().first)
            assertTrue(model.beginShow())
        }

    @Test
    fun `reset restores bidding and enabled ads from fixed source with ads disabled`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings(AdDebugControls(AdSource.MAX, false))
            val model = model(settings)
            runCurrent()

            model.resetDefaults()
            runCurrent()

            assertEquals(listOf("reset"), settings.mutations)
            assertEquals(AdDebugControls(AdSource.BIDDING, true), settings.controls)
            assertEquals(AdSource.BIDDING, model.state.value.source)
            assertTrue(model.state.value.globalEnabled)
            assertEquals(messages.restored, model.state.value.result)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun `cancel and finish release preview gate and bound returned SDK messages`() =
        runTest(mainDispatcher.dispatcher) {
            val model = model(FakeAdDebugSettings())
            runCurrent()
            assertTrue(model.beginShow())

            model.cancelShow()
            assertFalse(model.state.value.busy)
            assertEquals(messages.cancelled, model.state.value.result)
            runCurrent()
            assertTrue(model.beginShow())

            model.finishShow("SDK failure: " + "x".repeat(5_000))
            assertFalse(model.state.value.busy)
            assertEquals(2_000, model.state.value.result.length)
            assertTrue(model.state.value.result.startsWith("SDK failure: "))
            runCurrent()
            assertTrue(model.beginShow())
        }

    @Test
    fun `SDK becoming unavailable prevents a subsequent preview`() =
        runTest(mainDispatcher.dispatcher) {
            val model = model(FakeAdDebugSettings())
            runCurrent()
            assertTrue(model.beginShow())

            sdkState.value = AdInitializationState.FAILED
            runCurrent()
            model.cancelShow()
            runCurrent()

            assertFalse(model.state.value.busy)
            assertFalse(model.state.value.sdkReady)
            assertFalse(model.beginShow())
            assertTrue(model.state.value.diagnostics.startsWith("SDK: FAILED\n"))
        }

    @Test
    fun `clearing model cancels an unfinished edit before it changes SDK controls`() =
        runTest(mainDispatcher.dispatcher) {
            val settings = FakeAdDebugSettings()
            val model = model(settings)
            runCurrent()
            settings.sourceWriteGate = CompletableDeferred()

            model.selectSource(AdSource.GAM)
            runCurrent()
            assertEquals(1, settings.activeCalls)
            models.clear()
            runCurrent()

            assertEquals(0, settings.activeCalls)
            assertEquals(AdSource.BIDDING, settings.controls.source)
        }

    private fun model(
        settings: FakeAdDebugSettings,
        saved: SavedStateHandle = SavedStateHandle(),
    ): AdDebugViewModel = AdDebugViewModel(saved, settings, sdkState, messages).also {
        models.put("ad-debug-${modelNumber++}", it)
    }

    private companion object {
        const val FORMAT_KEY = "debug.ads.format"
    }
}

/** 可挂起或失败的边界替身，用于模拟真实 SDK 持久化访问，而不初始化广告运行时。 */
private class FakeAdDebugSettings(
    var controls: AdDebugControls = AdDebugControls(AdSource.BIDDING, true),
) : AdDebugSettings {
    var controlsReadGate: CompletableDeferred<Unit>? = null
    var sourceWriteGate: CompletableDeferred<Unit>? = null
    var readFailure: Exception? = null
    var sourceWriteFailure: Exception? = null
    var diagnosticFailure: Exception? = null
    val diagnosticText = "actual test SDK status"
    val mutations = mutableListOf<String>()
    val diagnosticQueries = mutableListOf<Pair<DebugAdFormat, AdSource>>()
    var activeCalls = 0
        private set
    var maxConcurrentCalls = 0
        private set

    override suspend fun readControls(): AdDebugControls = call {
        controlsReadGate?.await()
        readFailure?.let { throw it }
        controls
    }

    override suspend fun setSource(source: AdSource) = call {
        mutations += "source:${source.name}"
        sourceWriteGate?.await()
        sourceWriteFailure?.let { throw it }
        controls = controls.copy(source = source)
    }

    override suspend fun setGlobalEnabled(enabled: Boolean) = call {
        mutations += "global:$enabled"
        controls = controls.copy(globalEnabled = enabled)
    }

    override suspend fun resetDefaults() = call {
        mutations += "reset"
        controls = AdDebugControls(AdSource.BIDDING, true)
    }

    override suspend fun diagnostics(format: DebugAdFormat, source: AdSource): String = call {
        diagnosticQueries += format to source
        diagnosticFailure?.let { throw it }
        diagnosticText
    }

    private suspend fun <T> call(operation: suspend () -> T): T {
        activeCalls++
        maxConcurrentCalls = maxOf(maxConcurrentCalls, activeCalls)
        return try {
            operation()
        } finally {
            activeCalls--
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdDebugMainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
