package com.example.aicleanphonestorage.feature.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.aicleanphonestorage.feature.notifications.data.NotificationRulesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationRulesStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `saved opt ins survive store restart and opt out persists`() = runTest {
        val file = temporary.root.resolve("rules.preferences_pb")
        suspend fun session(block: suspend (NotificationRulesStore) -> Unit) {
            val job = SupervisorJob()
            val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
            val data = PreferenceDataStoreFactory.create(scope = scope) { file }
            try { block(NotificationRulesStore(data)) } finally { job.cancelAndJoin() }
        }
        session { rules ->
            assertEquals(emptySet<String>(), rules.selectedPackages.first())
            rules.setEnabled("app.a", true)
            rules.setEnabled("app.b", true)
            assertEquals(setOf("app.a", "app.b"), rules.selectedPackages.first())
        }
        session { rules ->
            assertEquals(setOf("app.a", "app.b"), rules.selectedPackages.first())
            rules.setEnabled("app.a", false)
        }
        session { rules -> assertEquals(setOf("app.b"), rules.selectedPackages.first()) }
    }
}
