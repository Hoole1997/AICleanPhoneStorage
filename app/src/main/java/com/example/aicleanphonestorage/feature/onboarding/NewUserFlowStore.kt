package com.example.aicleanphonestorage.feature.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.newUserFlowDataStore by preferencesDataStore("new_user_flow")

internal interface NewUserFlowStore {
    suspend fun eligible(): Boolean
    suspend fun interstitialStarted()
    suspend fun homeReached()
}

/**
 * 以安装为单位持久化，不依赖无法保证调用的 onDestroy。点击后先落盘，再请求插屏。
 * pending 在开屏/设备扫描阶段保持不变；插屏阶段退出不重走，首页到达后记录 completed。
 */
internal class PersistentNewUserFlowStore(
    private val store: DataStore<Preferences>,
    private val legacyHomeExists: () -> Boolean,
) : NewUserFlowStore {
    constructor(context: Context) : this(context.applicationContext.newUserFlowDataStore,
        legacyHomeCheck(context.applicationContext))

    override suspend fun eligible(): Boolean = withContext(Dispatchers.IO) {
        // 老版本首页已创建扫描索引：首次迁移时跳过引导。首次判定后立即写入 pending，
        // 避免本次引擎初始化产生的索引被下个进程误判成老用户。
        val snapshot = store.edit { prefs ->
            if (prefs[STAGE] == null) prefs[STAGE] =
                if (legacyHomeExists()) COMPLETED else PENDING
        }
        snapshot[STAGE] == PENDING
    }

    override suspend fun interstitialStarted() { store.edit { it[STAGE] = INTERSTITIAL } }
    override suspend fun homeReached() { store.edit { it[STAGE] = COMPLETED } }

    private companion object {
        fun legacyHomeCheck(app: Context): () -> Boolean = { app.getDatabasePath("cleanup_index.db").exists() }
        val STAGE = stringPreferencesKey("stage")
        const val PENDING = "pending"
        const val INTERSTITIAL = "interstitial"
        const val COMPLETED = "completed"
    }
}
