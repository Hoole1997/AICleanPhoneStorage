package com.example.aicleanphonestorage.feature.retention

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
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetentionTest {
    @Test fun remoteConfigDefaultsNeverPromoteOrganicOrUnknownAudience() {
        val inputs = listOf(null, "", "broken", "{}", "{paid:{enabled:false}}", "{\"paid\":{\"enabled\":\"false\"}}",
            "{\"paid\":{\"enabled\":true},\"organic\":{\"enabled\":true}}")
        for (raw in inputs) {
            assertTrue(raw, RetentionPolicy.enabled(raw, true))
            assertFalse(raw, RetentionPolicy.enabled(raw, false))
        }
        assertFalse(RetentionPolicy.enabled("{\"paid\":{\"enabled\":false}}", true))
        assertTrue(RetentionPolicy.enabled("{\"paid\":{\"enabled\":false}} trailing", true))
    }

    @Test fun feedbackIsBoundedAndSentOnlyForOtherOnReasonPageButtonClick() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val model = RetentionViewModel(SavedStateHandle(), EventSink { event, params -> calls += event to params })
        model.shown(); model.shown()
        assertTrue(model.beginAnyway()); assertFalse(model.beginAnyway())
        assertEquals(RetentionPage.OFFER, model.state.value.page)
        model.adFinished(); model.shown(); model.shown()
        model.reason = 5
        model.feedback = "test-feedback".repeat(80)
        assertEquals(500, model.feedback.length)
        assertEquals(3, calls.size) // 选择和输入本身不发送事件。
        model.uninstall(); model.keep()
        assertEquals(listOf(MetricEvent.UNINSTALL_RETENTION_SHOW, MetricEvent.UNINSTALL_RETENTION_CLICK,
            MetricEvent.UNINSTALL_REASON_SHOW, MetricEvent.UNINSTALL_REASON_CLICK, MetricEvent.UNINSTALL_REASON_CLICK), calls.map { it.first })
        assertEquals(mapOf("action" to "uninstall", "reason" to "other", "has_other_content" to model.feedback), calls[3].second)
        assertEquals(mapOf("action" to "keep_app", "reason" to "other", "has_other_content" to model.feedback), calls[4].second)
        assertEquals(mapOf("action" to "uninstall"), calls[1].second)
        model.feedback = "changed after click"
        assertEquals(500, (calls[3].second["has_other_content"] as String).length)
    }

    @Test fun currentCleaningReasonsUseDocumentEnumsAndNeverInventSimilarAppSelection() {
        val calls = mutableListOf<Map<String, Any>>()
        val model = RetentionViewModel(SavedStateHandle(), EventSink { _, params -> calls += params })
        model.beginAnyway(); model.adFinished(); calls.clear()
        val reasons = listOf(null to "none", 0 to "difficult_to_use", 1 to "too_many_ads",
            2 to "poor_user_experience", 3 to "unmet_needs", 4 to "poor_user_experience", 5 to "other", 99 to "none")
        for ((selected, wire) in reasons) {
            model.reason = selected
            model.feedback = "  test input\n原文  "
            model.keep(); model.uninstall()
            for (parameters in calls.takeLast(2)) {
                assertEquals(wire, parameters["reason"])
                assertEquals(if (selected == 5) model.feedback else "", parameters["has_other_content"])
                assertTrue(parameters["has_other_content"] is String)
            }
        }
        assertFalse(calls.any { it["reason"] == "found_similar_app" })
    }

    @Test fun editingOtherThenSwitchingReasonOrReturningToOfferDoesNotSendStaleText() {
        val calls = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
        val saved = SavedStateHandle()
        val model = RetentionViewModel(saved, EventSink { event, params -> calls += event to params })
        model.beginAnyway(); model.adFinished(); calls.clear()
        model.reason = 5; model.feedback = "test draft"
        assertTrue(calls.isEmpty())
        val restored = RetentionViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }),
            EventSink { event, params -> calls += event to params })
        restored.keep()
        assertEquals("test draft", calls.last().second["has_other_content"])
        restored.reason = 1; restored.uninstall()
        assertEquals("", calls.last().second["has_other_content"])
        restored.reason = 5; restored.feedback = ""; restored.keep()
        assertEquals("other", calls.last().second["reason"])
        assertEquals("", calls.last().second["has_other_content"])
        restored.back(); restored.keep()
        assertEquals(MetricEvent.UNINSTALL_RETENTION_CLICK to mapOf("action" to "keep_app"), calls.last())
    }

    @Test fun recreationKeepsFeedbackAndDoesNotReplayPendingAd() {
        val saved = SavedStateHandle()
        val model = RetentionViewModel(saved, EventSink { _, _ -> })
        model.reason = 1; model.feedback = "local only"
        model.shown(); model.beginAnyway()
        val copy = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = RetentionViewModel(copy, EventSink { _, _ -> })
        assertEquals(RetentionPage.REASON, restored.state.value.page)
        assertFalse(restored.state.value.busy)
        assertFalse(restored.beginAnyway())
        assertEquals(1, restored.reason)
        assertEquals("local only", restored.feedback)
        assertTrue(restored.back())
        assertEquals(RetentionPage.OFFER, restored.state.value.page)
        assertFalse(restored.back())
        restored.reason = 99
        assertNull(restored.reason)
    }

    @Test fun featureRoutesUseExistingPermissionProtectedDestinations() {
        assertEquals(setOf(NotificationDestination.CLEAN, NotificationDestination.MALWARE,
            NotificationDestination.DUPLICATE_PHOTOS, NotificationDestination.SCREENSHOTS,
            NotificationDestination.LARGE_FILES, NotificationDestination.NOTIFICATION_CLEANER),
            RetentionFeature.entries.map { it.destination }.toSet())
        val events = mutableListOf<Map<String, Any>>()
        val vm = RetentionViewModel(SavedStateHandle(), EventSink { _, params -> events += params })
        RetentionFeature.entries.forEach(vm::feature)
        assertEquals(RetentionFeature.entries.map { mapOf("feature_name" to it.wire) }, events)
    }

    @Test fun shortcutRouteWaitsForSplashThenRechecksRemoteGate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            for (enabled in listOf(true, false)) {
                var checks = 0
                val saved = SavedStateHandle()
                val model = StartupViewModel(saved, clock = { testScheduler.currentTime },
                    resolveNewUserFlow = { error("Shortcut must not enter onboarding") },
                    resolveRetention = { checks++; enabled }) {}
                retained.put("$enabled", model)
                model.accept(StartupEntry(NotificationDestination.HOME, uninstallRetention = true))
                model.permissionFinished(); model.consentFinished(); runCurrent()
                val id = requireNotNull(model.beginAd())
                advanceTimeBy(3_000); runCurrent()
                assertNull(model.consume()); assertEquals(0, checks)
                model.adFinished(id); assertNull(model.consume()); runCurrent()
                assertTrue(requireNotNull(model.consume()).uninstallRetention)
                assertEquals(enabled, model.state.value.retentionAllowed)
                assertEquals(false, model.state.value.newUserFlow)
                assertEquals(1, checks); assertNull(model.consume())
            }
        } finally { retained.clear(); Dispatchers.resetMain() }
    }

    @Test fun aNewNotificationReplacesSuspendedShortcutRouteWithoutSecondSplash() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val retained = ViewModelStore()
        try {
            val gate = CompletableDeferred<Boolean>()
            val vm = StartupViewModel(SavedStateHandle(), clock = { testScheduler.currentTime }, resolveRetention = { gate.await() }) {}
            retained.put("vm", vm)
            vm.accept(StartupEntry(NotificationDestination.HOME, uninstallRetention = true))
            vm.permissionFinished(); vm.consentFinished(); runCurrent()
            val ad = requireNotNull(vm.beginAd()); vm.adFinished(ad); advanceTimeBy(3_000); runCurrent()
            vm.consume(); runCurrent()
            vm.accept(StartupEntry(NotificationDestination.CLEAN))
            gate.complete(true); runCurrent()
            assertNull(vm.beginAd())
            assertNull(vm.consume()); runCurrent()
            assertEquals(NotificationDestination.CLEAN, vm.consume()?.destination)
            assertEquals(false, vm.state.value.retentionAllowed)
        } finally { retained.clear(); Dispatchers.resetMain() }
    }
}
