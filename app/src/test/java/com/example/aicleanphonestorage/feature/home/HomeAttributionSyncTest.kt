package com.example.aicleanphonestorage.feature.home

import com.example.aicleanphonestorage.feature.home.data.HomeCleaningState
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import net.corekit.core.controller.ChannelUserController.UserChannelType
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeAttributionSyncTest {
    @Test fun attributionUpdatesHomeAndPushWithTheSameAudience() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val home = HomeCleaningState(false, { 0 }, { 3201 })
            val updates = mutableListOf<Boolean>()
            val sync = HomeCleaningSync(home, {}, updates::add)
            sync.onChannelChanged(UserChannelType.NATURAL, UserChannelType.PAID)
            runCurrent()
            assertTrue(home.state.value.paidUser)
            sync.onChannelChanged(UserChannelType.PAID, UserChannelType.NATURAL)
            runCurrent()
            assertFalse(home.state.value.paidUser)
            assertEquals(listOf(true, false), updates)
        } finally { Dispatchers.resetMain() }
    }
}
