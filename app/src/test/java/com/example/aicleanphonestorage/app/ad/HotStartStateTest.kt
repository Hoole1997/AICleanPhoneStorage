package com.example.aicleanphonestorage.app.ad

import com.android.common.bill.ads.config.AdPlatform
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import org.junit.Assert.*
import org.junit.Test

class HotStartStateTest {
    @Test fun coldLaunchAndActivitySwitchDoNotCountAsHotStart() {
        val state = HotStartState()
        state.foreground()
        assertFalse(state.take())
        assertFalse(state.take())
        state.background(true)
        state.foreground()
        assertTrue(state.take())
        assertFalse(state.take())
    }

    @Test fun permissionAndAdReturnAreSkippedButNextNormalReturnIsEligible() {
        val state = HotStartState()
        state.foreground()
        state.background(false)
        state.foreground()
        assertFalse(state.take())
        state.background(true)
        state.foreground()
        assertTrue(state.take())
    }

    @Test fun eachSingleAllowedPlatformIsEnoughWhenTotalAllows() {
        AdPlatform.entries.forEach { platform ->
            assertTrue(HotStartAdEligibility.frequencyAllowed(true, setOf(platform)))
        }
    }

    @Test fun totalBlockOrNoAllowedPlatformsStillRejectsHotStart() {
        assertFalse(HotStartAdEligibility.frequencyAllowed(false, AdPlatform.entries.toSet()))
        assertFalse(HotStartAdEligibility.frequencyAllowed(true, emptySet()))
        assertFalse(HotStartAdEligibility.frequencyAllowed(false, emptySet()))
    }

    @Test fun gamDailyLimitDoesNotBlockOtherAllowedPlatforms() {
        assertTrue(HotStartAdEligibility.frequencyAllowed(true,
            setOf(AdPlatform.ADMOB, AdPlatform.TOPON, AdPlatform.PANGLE)))
    }

    @Test fun nestedExternalTransitionsReleaseIndependentlyAndOnlyOnce() {
        val first = ForegroundTransitionGuard.hold()
        val second = ForegroundTransitionGuard.hold()
        assertTrue(ForegroundTransitionGuard.blocked)
        first.close()
        first.close()
        assertTrue(ForegroundTransitionGuard.blocked)
        second.close()
        assertFalse(ForegroundTransitionGuard.blocked)
    }
}
