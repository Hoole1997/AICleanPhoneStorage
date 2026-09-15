package io.docview.push.analytics

import org.junit.Assert.*
import org.junit.Test

class NotificationVisibilityStateTest {
    @Test fun boundedContentDoesNotLeaveHalfAnEmojiAtTheBoundary() {
        val content = NotificationContent("a" + "😀".repeat(200), "a" + "😀".repeat(600)).bounded()
        assertFalse(Character.isHighSurrogate(content.title.last()))
        assertFalse(Character.isHighSurrogate(content.text.last()))
        assertTrue(content.title.length <= 120)
        assertTrue(content.text.length <= 500)
    }

    @Test fun coldEntryAndBackgroundRestartAreCapturedBeforeResume() {
        val state = NotificationVisibilityState()
        assertTrue(state.backgroundForClick())
        state.started()
        assertTrue(state.backgroundForClick())
        assertFalse(state.backgroundForDisplay())
        state.resumed()
        assertFalse(state.backgroundForClick())
        state.stopped()
        assertTrue(state.backgroundForDisplay())
        state.started()
        assertTrue("onNewIntent may run after onStart but before onResume", state.backgroundForClick())
    }

    @Test fun foregroundNavigationAndNewIntentRemainForeground() {
        val state = NotificationVisibilityState()
        state.started(); state.resumed()
        assertFalse(state.backgroundForClick())
        state.started()
        assertFalse(state.backgroundForClick())
        state.resumed(); state.stopped()
        assertFalse(state.backgroundForDisplay())
        state.stopped()
        assertTrue(state.backgroundForClick())
    }

    @Test fun requiredFieldsArePresentAndBoundedWithFirebaseCompatibleBackgroundValue() {
        val content = NotificationContent("t".repeat(500), "x".repeat(5000))
        val values = content.properties(true)
        assertEquals("true", values["from_background"])
        assertEquals(120, (values["title"] as String).length)
        assertEquals(500, (values["text"] as String).length)
        assertEquals("false", content.properties(false)["from_background"])
        assertEquals("", NotificationContent("", "").properties(false)["text"])
    }
}
