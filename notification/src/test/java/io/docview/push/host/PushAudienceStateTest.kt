package io.docview.push.host

import io.docview.push.config.ContentController
import org.junit.Assert.*
import org.junit.Test

class PushAudienceStateTest {
    @Test fun neitherConfiguredDefaultStartsDayPoolsBeforeAttributionConfirmation() {
        for (channel in PushUserChannel.UserChannelType.entries) {
            val state = PushAudienceState(channel)
            assertEquals(channel, state.channel)
            assertFalse(state.confirmedPaid)
        }
    }

    @Test fun confirmingTheSamePaidDefaultStillEnablesThePool() {
        val state = PushAudienceState(PushUserChannel.UserChannelType.PAID)
        assertFalse(state.confirmedPaid)
        assertEquals(PushUserChannel.UserChannelType.PAID, state.confirm(PushUserChannel.UserChannelType.PAID))
        assertTrue(state.confirmedPaid)
    }

    @Test fun naturalUsersRemainBlockedAndChannelChangesTakeEffectImmediately() {
        val state = PushAudienceState(PushUserChannel.UserChannelType.NATURAL)
        state.confirm(PushUserChannel.UserChannelType.NATURAL)
        assertFalse(state.confirmedPaid)
        state.confirm(PushUserChannel.UserChannelType.PAID)
        assertTrue(state.confirmedPaid)
        state.confirm(PushUserChannel.UserChannelType.NATURAL)
        assertFalse(state.confirmedPaid)
        state.confirm(PushUserChannel.UserChannelType.PAID)
        assertTrue(state.confirmedPaid)
    }

    @Test fun naturalContentReadReturnsBeforeTouchingAndroidStorageOrRotation() {
        PushUserChannel.setChannel(PushUserChannel.UserChannelType.NATURAL)
        assertNull(ContentController.getNextContent())
        assertFalse(ContentController.isInitialized())
    }
}
