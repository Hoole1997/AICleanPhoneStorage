package com.example.aicleanphonestorage.feature.notifications

import com.example.aicleanphonestorage.feature.notifications.service.NotificationCandidate
import com.example.aicleanphonestorage.feature.notifications.service.NotificationClearPolicy
import org.junit.Assert.*
import org.junit.Test

class NotificationClearPolicyTest {
    private val candidate = NotificationCandidate("key", "selected.app", true, true, false, false)
    @Test fun `default empty rules never clear anything`() {
        assertFalse(NotificationClearPolicy.shouldClear(candidate, emptySet(), "cleaner"))
    }
    @Test fun `only opted in apps are eligible and opting out immediately changes policy`() {
        assertTrue(NotificationClearPolicy.shouldClear(candidate, setOf("selected.app"), "cleaner"))
        assertFalse(NotificationClearPolicy.shouldClear(candidate, setOf("another.app"), "cleaner"))
    }
    @Test fun `ongoing foreground service other profile and nonclearable are protected`() {
        for (protected in listOf(candidate.copy(ongoing = true), candidate.copy(foregroundService = true), candidate.copy(clearable = false), candidate.copy(currentUser = false))) {
            assertFalse(NotificationClearPolicy.shouldClear(protected, setOf("selected.app"), "cleaner"))
        }
        assertFalse(NotificationClearPolicy.shouldClear(candidate, setOf("selected.app"), "selected.app"))
    }
}
