package com.example.aicleanphonestorage.feature.rating

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RatingPromptViewModelTest {
    @Test fun claimAndExposureAreSingleAndRestoreDoesNotRepeat() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owners = ViewModelStore()
        try {
            var claims = 0
            val events = mutableListOf<MetricEvent>()
            val saved = SavedStateHandle()
            val model = RatingPromptViewModel({ claims++; true }, saved) { event, _ -> events += event }
            owners.put("first", model)
            model.claim(); model.claim()
            runCurrent()
            assertEquals(1, claims)
            assertEquals(RatingPhase.READY, model.phase.value)
            assertTrue(events.isEmpty())
            model.shown(); model.shown()
            assertEquals(listOf(MetricEvent.RATE_SHOW), events)
            val restored = RatingPromptViewModel({ error("Do not claim again") }, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })) { event, _ -> events += event }
            owners.put("restored", restored)
            restored.shown()
            assertEquals(1, events.size)
        } finally { owners.clear(); Dispatchers.resetMain() }
    }

    @Test fun onlyHighRatingsRequestPlayAndDuplicateClickIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owners = ViewModelStore()
        try {
            for (stars in 1..5) {
                val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
                val model = RatingPromptViewModel({ true }, SavedStateHandle()) { e, p -> events += e to p }
                owners.put("$stars", model)
                model.claim(); runCurrent(); model.shown()
                model.decide(stars, true); model.decide(stars, true); model.decide(stars, false)
                assertEquals(MetricEvent.RATE_CLICK to mapOf("stars" to stars, "action" to if (stars >= 4) "store" else "close"), events.last())
                assertEquals(2, events.size)
                assertEquals(stars >= 4, model.beginReview()); assertFalse(model.beginReview())
                model.finish()
                assertEquals(RatingPhase.FINISHED, model.phase.value)
            }
        } finally { owners.clear(); Dispatchers.resetMain() }
    }

    @Test fun dismissalAndPreviouslySeenPromptDoNotRequestPlay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owners = ViewModelStore()
        try {
            val values = mutableListOf<Map<String, Any>>()
            val model = RatingPromptViewModel({ true }, SavedStateHandle()) { _, p -> values += p }
            owners.put("dismiss", model)
            model.claim(); runCurrent(); model.shown(); model.decide(3, false)
            assertEquals(mapOf("stars" to 3, "action" to "close"), values.last())
            assertFalse(model.beginReview())
            val seen = RatingPromptViewModel({ false }, SavedStateHandle()) { _, _ -> fail("No exposure") }
            owners.put("seen", seen); seen.claim(); runCurrent()
            assertEquals(RatingPhase.FINISHED, seen.phase.value)
        } finally { owners.clear(); Dispatchers.resetMain() }
    }

    @Test fun diskFailureAndRestoredNativeFlowCannotCreateAnotherPrompt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owners = ViewModelStore()
        try {
            val failed = RatingPromptViewModel({ throw java.io.IOException() }, SavedStateHandle()) { _, _ -> fail("No exposure") }
            owners.put("failed", failed); failed.claim(); runCurrent()
            assertEquals(RatingPhase.FINISHED, failed.phase.value)
            val restored = RatingPromptViewModel({ error("No second claim") }, SavedStateHandle(mapOf("rating.phase" to "REVIEW_RUNNING"))) { _, _ -> fail("No replay") }
            owners.put("restored", restored)
            assertEquals(RatingPhase.FINISHED, restored.phase.value)
            assertFalse(restored.beginReview())
        } finally { owners.clear(); Dispatchers.resetMain() }
    }
}
