package com.example.aicleanphonestorage.app.ad

import androidx.lifecycle.ViewModelStore
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import org.junit.Assert.*
import org.junit.Test

class InterstitialActionStateTest {
    @Test fun onlySdkCompletionMakesAnActionConsumable() {
        val state = InterstitialActionState()
        val id = requireNotNull(state.begin("clean"))
        assertNull(state.consume())
        assertNull(state.begin("exit"))
        state.complete(id)
        assertEquals("clean", state.consume())
        state.complete(id)
        assertNull(state.consume())
    }

    @Test fun confirmedOperationIdSurvivesWaitingAndDuplicateCallbacks() {
        val state = InterstitialActionState()
        val id = requireNotNull(state.begin("confirmed", payload = 42L))
        state.complete(id)
        state.complete(id)
        assertEquals(42L, state.pending.value!!.payload)
        assertEquals("confirmed", state.consume())
        assertNull(state.consume())
    }

    @Test fun staleCallbackCannotCompleteNewAction() {
        val state = InterstitialActionState()
        val first = requireNotNull(state.begin("clean"))
        state.complete(first); state.consume()
        val second = requireNotNull(state.begin("exit"))
        state.complete(first)
        assertNull(state.consume())
        state.complete(second)
        assertEquals("exit", state.consume())
    }

    @Test fun destroyedOwnerDoesNotResumeItsPendingAction() {
        val state = InterstitialActionState()
        val store = ViewModelStore().apply { put("ads", state) }
        val id = requireNotNull(state.begin("clean"))
        store.clear()
        state.complete(id)
        assertNull(state.consume())
        assertNull(state.begin("exit"))
    }

    @Test fun cleanupPlacementsCoverEverySupportedFeatureWithoutCollisions() {
        val features = CleanupFeature.entries
        assertEquals(features.size, features.map(InterstitialPlacements::clean).toSet().size)
        assertEquals(features.size, features.map(InterstitialPlacements::exit).toSet().size)
        assertEquals(features.size, features.map(InterstitialPlacements::completionExit).toSet().size)
        assertTrue(features.all { InterstitialPlacements.clean(it) != InterstitialPlacements.exit(it) })
    }
}
