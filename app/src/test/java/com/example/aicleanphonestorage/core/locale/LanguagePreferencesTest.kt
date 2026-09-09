package com.example.aicleanphonestorage.core.locale

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanguagePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun explicitLanguageAndSystemChoiceSurviveRestart() = runTest {
        val file = temporary.root.resolve("language.preferences_pb")
        suspend fun session(block: suspend (LanguagePreferences) -> Unit) {
            val job = SupervisorJob()
            val store =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
                ) {
                    file
                }
            try {
                block(LanguagePreferences(store))
            } finally {
                job.cancelAndJoin()
            }
        }
        session {
            assertEquals(StoredLanguage(), it.read())
            it.write("ar", false)
        }
        session {
            assertEquals("ar", it.read().tag)
            it.write("", true)
        }
        session { assertEquals(StoredLanguage("", true), it.read()) }
    }

    @Test
    fun languageCatalogKeepsSystemDistinctFromExplicitEnglish() {
        assertTrue(AppLanguages.valid(""))
        assertTrue(AppLanguages.valid("en"))
        assertEquals("en", AppLanguages.matching("en-US"))
        assertEquals("zh-Hans", AppLanguages.matching("zh-Hans-CN"))
        assertEquals("", AppLanguages.matching(""))
        assertFalse(AppLanguages.valid("system"))
        assertEquals(16, AppLanguages.supported.map { it.tag }.toSet().size)
    }
}
