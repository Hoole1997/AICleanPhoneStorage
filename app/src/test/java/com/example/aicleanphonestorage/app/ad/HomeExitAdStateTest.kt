package com.example.aicleanphonestorage.app.ad

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class HomeExitAdStateTest {
    @Test fun pendingExitSurvivesRestoreButConsumedIntentDoesNotReplay() {
        val saved = SavedStateHandle()
        val request = HomeExitAdRequest("first", "screenshots_exit_interstitial")
        HomeExitAdState(saved).accept(request)
        val restored = HomeExitAdState(copy(saved))
        assertEquals(request, restored.pending)
        assertEquals(request, restored.consume())
        assertNull(restored.consume())
        restored.accept(request)
        assertNull(restored.pending)
    }

    @Test fun consumedTokenSurvivesProcessStateRestoreAndNewExitKeepsItsSource() {
        val saved = SavedStateHandle()
        val first = HomeExitAdRequest("first", "junk_exit_interstitial")
        HomeExitAdState(saved).apply { accept(first); consume() }
        val restored = HomeExitAdState(copy(saved))
        restored.accept(first)
        assertNull(restored.pending)
        val next = HomeExitAdRequest("next", "photo_compress_complete_exit_interstitial")
        restored.accept(next)
        assertEquals(next, restored.consume())
    }

    private fun copy(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
