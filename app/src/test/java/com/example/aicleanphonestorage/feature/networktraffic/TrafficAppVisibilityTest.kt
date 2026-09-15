package com.example.aicleanphonestorage.feature.networktraffic

import android.content.pm.ApplicationInfo
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficAppVisibility
import org.junit.Assert.*
import org.junit.Test

class TrafficAppVisibilityTest {
    @Test fun systemRemovedSandboxAndIsolatedUidsAreExcluded() {
        for (uid in listOf(-1, 0, 1000, 9999, 20_000, 99_000, 101_000))
            assertFalse("Unexpected application UID: $uid", TrafficAppVisibility.isApplicationUid(uid))
        for (uid in listOf(10_000, 19_999, 110_123))
            assertTrue(TrafficAppVisibility.isApplicationUid(uid))
    }

    @Test fun bothPreinstalledAndUpdatedSystemAppsAreExcluded() {
        assertFalse(TrafficAppVisibility.isUserInstalled(ApplicationInfo.FLAG_SYSTEM))
        assertFalse(TrafficAppVisibility.isUserInstalled(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertFalse(TrafficAppVisibility.isUserInstalled(ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertTrue(TrafficAppVisibility.isUserInstalled(0))
        assertTrue(TrafficAppVisibility.isUserInstalled(ApplicationInfo.FLAG_DEBUGGABLE))
    }
}
