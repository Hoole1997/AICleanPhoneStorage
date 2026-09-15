package com.example.aicleanphonestorage.feature.notifications.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.notificationRulesDataStore by preferencesDataStore(name = "notification_rules")

/** DataStore 单例负责磁盘I/O和原子写入；只存包名集合，不存通知标题/正文/附件。 */
class NotificationRulesStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.notificationRulesDataStore)
    val selectedPackages = store.data.map { it[PACKAGES]?.toSet().orEmpty() }.distinctUntilChanged()

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        require(packageName.isNotBlank())
        // 已提交的用户选择必须完成原子落盘，即使用户立即返回页面；DataStore自身在线程池写盘。
        // NonCancellable仅包住这次小型事务，不创建常驻协程，不屏蔽磁盘失败。
        withContext(NonCancellable) {
            store.edit { preferences ->
                val selected = preferences[PACKAGES].orEmpty().toMutableSet()
                if (enabled) selected.add(packageName) else selected.remove(packageName)
                preferences[PACKAGES] = selected
            }
        }
    }
    suspend fun setSelection(packages: Set<String>) {
        require(packages.all { it.isNotBlank() })
        // 用户按 Done 后一次原子提交，监听服务不会看到逐项勾选的草稿。
        withContext(NonCancellable) { store.edit { it[PACKAGES] = packages.toSet() } }
    }
    companion object { private val PACKAGES = stringSetPreferencesKey("auto_clear_packages") }
}
