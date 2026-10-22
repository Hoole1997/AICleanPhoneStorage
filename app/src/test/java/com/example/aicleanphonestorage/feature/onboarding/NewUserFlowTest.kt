package com.example.aicleanphonestorage.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.startup.StartupEntry
import com.example.aicleanphonestorage.feature.startup.StartupViewModel
import io.docview.push.NotificationDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewUserFlowTest {
    @Test fun actualCompletionUnknownAndEngineRetryHaveSingleTelemetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            var engineFails = true
            val calls = mutableListOf<DeviceInfoField>()
            val events = mutableListOf<MetricEvent>()
            val vm = DeviceScanViewModel(SavedStateHandle(), DeviceInfoSource {
                calls += it
                if (it == DeviceInfoField.DEVICE || (it == DeviceInfoField.ENGINE && engineFails)) error("read failed")
                if (it == DeviceInfoField.ENGINE) DeviceInfoValue.Done else DeviceInfoValue.Text("value")
            }, MemoryStore(), EventSink { event, params -> assertTrue(params.isEmpty()); events += event })
            retained.put("vm", vm)
            vm.startClean()
            assertFalse(vm.state.value.continuing)
            vm.foreground(); runCurrent()
            assertEquals(DeviceInfoValue.Unknown, vm.state.value.values[0])
            assertTrue(vm.state.value.engineFailed)
            assertFalse(vm.state.value.complete)
            assertEquals(listOf(MetricEvent.NEW_USER_SCAN_EXPOSURE), events)
            engineFails = false
            vm.retry(); runCurrent()
            assertTrue(vm.state.value.complete)
            assertEquals(2, calls.count { it == DeviceInfoField.ENGINE })
            assertEquals(1, calls.count { it == DeviceInfoField.DEVICE })
            vm.background(); vm.foreground(); runCurrent()
            vm.startClean(); vm.startClean(); runCurrent()
            assertTrue(vm.takeAdRequest()); assertFalse(vm.takeAdRequest())
            assertFalse(vm.state.value.navigateHome)
            vm.adFinished(); vm.adFinished()
            assertTrue(vm.state.value.navigateHome)
            assertEquals(listOf(MetricEvent.NEW_USER_SCAN_EXPOSURE, MetricEvent.NEW_USER_SCAN_FINISH,
                MetricEvent.NEW_USER_START_CLEAN_CLICK), events)
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    @Test fun backgroundCancelsOnlyPendingReadAndContinuesCurrentFields() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            var wait = true
            var cancelled = false
            val calls = mutableListOf<DeviceInfoField>()
            val vm = DeviceScanViewModel(SavedStateHandle(), DeviceInfoSource {
                calls += it
                if (it == DeviceInfoField.RESOLUTION && wait) try { awaitCancellation() } finally { cancelled = true }
                if (it == DeviceInfoField.ENGINE) DeviceInfoValue.Done else DeviceInfoValue.Text("value")
            }, MemoryStore(), EventSink { _, _ -> })
            retained.put("vm", vm)
            vm.foreground(); runCurrent()
            assertEquals(2, vm.state.value.values.count { it != null })
            vm.background(); runCurrent()
            assertTrue(cancelled)
            advanceTimeBy(30_000); runCurrent()
            assertFalse(vm.state.value.complete)
            wait = false; vm.foreground(); runCurrent()
            assertTrue(vm.state.value.complete)
            assertEquals(1, calls.count { it == DeviceInfoField.DEVICE })
            assertEquals(2, calls.count { it == DeviceInfoField.RESOLUTION })
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    @Test fun clickMustPersistBeforeAdAndCannotRepeatWhenReturningFromAd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            val persisted = CompletableDeferred<Unit>()
            val store = object : NewUserFlowStore {
                override suspend fun eligible() = true
                override suspend fun interstitialStarted() { persisted.await() }
                override suspend fun homeReached() = Unit
            }
            val saved = SavedStateHandle()
            val vm = DeviceScanViewModel(saved, DeviceInfoSource { DeviceInfoValue.Done }, store, EventSink { _, _ -> })
            retained.put("vm", vm)
            vm.foreground(); runCurrent(); vm.startClean(); runCurrent()
            assertTrue(vm.state.value.continuing)
            assertFalse(vm.takeAdRequest())
            persisted.complete(Unit); runCurrent()
            assertTrue(vm.takeAdRequest())
            vm.background(); vm.foreground(); vm.startClean(); runCurrent()
            assertFalse(vm.takeAdRequest())
            assertFalse(vm.state.value.navigateHome)
            val recreated = DeviceScanViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
                DeviceInfoSource { error("Must route through startup") }, store, EventSink { _, _ -> fail() })
            retained.put("restored", recreated)
            assertTrue(recreated.processRestored)
            recreated.foreground(); runCurrent()
            assertTrue(recreated.state.value.values.all { it == null })
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedPersistenceCanRetryWithoutDoubleClickOrAd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            var fail = true
            val store = object : NewUserFlowStore {
                override suspend fun eligible() = true
                override suspend fun interstitialStarted() { if (fail) throw java.io.IOException() }
                override suspend fun homeReached() = Unit
            }
            val events = mutableListOf<MetricEvent>()
            val vm = DeviceScanViewModel(SavedStateHandle(), DeviceInfoSource { DeviceInfoValue.Done }, store,
                EventSink { event, _ -> events += event })
            retained.put("vm", vm)
            vm.foreground(); runCurrent(); vm.startClean(); runCurrent()
            assertTrue(vm.state.value.savingFailed)
            assertFalse(vm.takeAdRequest())
            fail = false; vm.startClean(); runCurrent()
            assertTrue(vm.takeAdRequest())
            assertEquals(1, events.count { it == MetricEvent.NEW_USER_START_CLEAN_CLICK })
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    @Test fun routeWaitsForSplashAndRejectsNotificationOrHotEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            for ((index, entry) in listOf(StartupEntry(NotificationDestination.HOME, launcher = true),
                StartupEntry(NotificationDestination.HOME), StartupEntry(NotificationDestination.HOME, hotStart = true),
                StartupEntry(NotificationDestination.CLEAN)).withIndex()) {
                var resolved = 0
                val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }, resolveNewUserFlow = {
                    resolved++; true
                }) {}
                retained.put("$index", vm)
                vm.accept(entry); vm.permissionFinished(); vm.consentFinished(); runCurrent()
                assertNull(vm.consume()); assertEquals(0, resolved)
                val ad = requireNotNull(vm.beginAd())
                advanceTimeBy(3_000); runCurrent()
                assertNull(vm.consume()); assertEquals(0, resolved)
                vm.adFinished(ad)
                assertNull(vm.consume()); runCurrent()
                assertEquals(entry, vm.consume())
                assertEquals(entry.launcher, vm.state.value.newUserFlow)
                assertEquals(if (entry.launcher) 1 else 0, resolved)
                assertNull(vm.consume())
            }
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    private class MemoryStore : NewUserFlowStore {
        var pending = true
        override suspend fun eligible() = pending
        override suspend fun interstitialStarted() { pending = false }
        override suspend fun homeReached() { pending = false }
    }
}
