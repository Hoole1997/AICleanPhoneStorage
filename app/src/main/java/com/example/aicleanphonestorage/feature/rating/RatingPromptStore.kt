package com.example.aicleanphonestorage.feature.rating

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private val Context.ratingPromptDataStore by preferencesDataStore("rating_prompt")

internal fun interface RatingPromptStore {
    /** 展示前原子领取一次资格；写盘失败不能当作成功，避免重启后重复弹出。 */
    suspend fun claim(): Boolean
}

internal class PersistentRatingPromptStore(private val data: DataStore<Preferences>) : RatingPromptStore {
    constructor(context: Context) : this(context.applicationContext.ratingPromptDataStore)

    override suspend fun claim(): Boolean {
        var claimed = false
        data.edit { values ->
            if (values[SHOWN] != true) {
                values[SHOWN] = true
                claimed = true
            }
        }
        return claimed
    }
    private companion object { val SHOWN = booleanPreferencesKey("prompt_shown") }
}
