package com.example.aicleanphonestorage.core.locale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.languageDataStore by preferencesDataStore("app_language")

internal data class StoredLanguage(val tag: String = "", val migratedToFramework: Boolean = false)

/** 旧系统的语言选择由 DataStore 异步落盘；不启用 AppCompat 的主线程 XML 自动存储。 */
internal class LanguagePreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.languageDataStore)

    suspend fun read(): StoredLanguage {
        val value = store.data.first()
        return StoredLanguage(value[TAG].orEmpty(), value[MIGRATED] ?: false)
    }

    suspend fun write(tag: String, migrated: Boolean) {
        store.edit {
            it[TAG] = tag
            it[MIGRATED] = migrated
        }
    }

    private companion object {
        val TAG = stringPreferencesKey("tag")
        val MIGRATED = booleanPreferencesKey("framework_migrated")
    }
}
