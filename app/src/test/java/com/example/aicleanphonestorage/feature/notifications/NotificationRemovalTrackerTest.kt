package com.example.aicleanphonestorage.feature.notifications

import com.example.aicleanphonestorage.feature.notifications.service.NotificationRemovalTracker
import org.junit.Assert.*
import org.junit.Test

class NotificationRemovalTrackerTest {
    @Test fun onlyAcknowledgedListenerRemovalsCountAndSourcesAreDeduplicated() {
        val tracker = NotificationRemovalTracker()
        tracker.requested("a", "app.one")
        tracker.requested("b", "app.one")
        tracker.requested("c", "app.two")
        tracker.requested("d", "app.three")
        assertEquals(0, tracker.drainCount())
        assertTrue(tracker.removed("a", true))
        assertFalse(tracker.removed("b", true))
        assertTrue(tracker.removed("c", true))
        assertFalse(tracker.removed("d", false))
        assertFalse(tracker.removed("unknown", true))
        assertEquals(2, tracker.drainCount())
        assertEquals(0, tracker.drainCount())
    }
    @Test fun pendingKeysAreBoundedAndDisconnectedSessionsDoNotLeakCounts() {
        val tracker = NotificationRemovalTracker(2)
        tracker.requested("old", "one")
        tracker.requested("next", "two")
        tracker.requested("new", "three")
        assertFalse(tracker.removed("old", true))
        tracker.clear()
        assertFalse(tracker.removed("new", true))
        assertEquals(0, tracker.drainCount())
    }
}
