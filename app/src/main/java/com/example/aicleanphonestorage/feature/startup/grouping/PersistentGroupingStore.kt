package com.example.aicleanphonestorage.feature.startup.grouping

import android.content.Context
import java.security.MessageDigest

/** 每个分组单独保存摘要键，避免保存远程原文；不淘汰历史值，清数据/重装后重新计算。仅在 IO 访问。 */
internal class PersistentGroupingStore(context: Context, private val name: String = "startup_grouping") : GroupingStore {
    private val app = context.applicationContext
    private val preferences by lazy { app.getSharedPreferences(name, Context.MODE_PRIVATE) }
    override fun contains(value: String) = preferences.getBoolean(key(value), false)
    override fun record(value: String) {
        check(preferences.edit().putBoolean(key(value), true).commit()) { "Grouping 去重记录保存失败" }
    }
    private fun key(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
