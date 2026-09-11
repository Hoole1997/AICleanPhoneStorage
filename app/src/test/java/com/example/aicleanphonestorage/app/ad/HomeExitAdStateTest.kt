package com.example.aicleanphonestorage.app.ad

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class HomeExitAdStateTest {
    @Test fun pendingExitSurvivesRestoreButConsumedIntentDoesNotReplay() {
        val saved = SavedStateHandle()
        val request = HomeExitAdRequest("first", "back_home_screenshots")
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
        val first = HomeExitAdRequest("first", "back_home_junk")
        HomeExitAdState(saved).apply { accept(first); consume() }
        val restored = HomeExitAdState(copy(saved))
        restored.accept(first)
        assertNull(restored.pending)
        val next = HomeExitAdRequest("next", "back_home_photo")
        restored.accept(next)
        assertEquals(next, restored.consume())
    }

    @Test fun restoredLegacyOrNonExitSlotCannotReachSdk() {
        for (placement in listOf("junk_exit_interstitial", "native_home", "clean_confirm_junk")) {
            val saved = SavedStateHandle(mapOf(
                "exit_ad.pending.token" to "old",
                "exit_ad.pending.placement" to placement,
            ))
            val restored = HomeExitAdState(saved)
            assertNull(restored.pending)
            assertNull(restored.consume())
            restored.accept(HomeExitAdRequest("invalid", placement))
            assertNull(restored.pending)
            val valid = HomeExitAdRequest("new", "back_home_junk")
            restored.accept(valid)
            assertEquals(valid, restored.consume())
        }
    }

    private fun copy(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
