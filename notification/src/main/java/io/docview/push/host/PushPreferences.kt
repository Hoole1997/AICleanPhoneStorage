package io.docview.push.host

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.launch

/** 首次在初始化 IO 任务中预载。同步源接口只访问小值缓存，落盘由同一应用级 Scope 处理。 */
internal object PushPreferences {
    private val values = ConcurrentHashMap<String, Any>()
    private lateinit var preferences: android.content.SharedPreferences
    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("browser_push_preferences", Context.MODE_PRIVATE)
        preferences.all.forEach { (key, value) -> if (value != null) values[key] = value }
    }
    fun value(key: String): Any? = values[key]
    @Synchronized fun put(key: String, value: Any) {
        values[key] = value
        PushEnvironment.scope.launch {
            // 并发写同一个 key 时总是写缓存最新值，旧任务不会覆盖新状态。
            synchronized(this@PushPreferences) {
                val editor = preferences.edit()
                when (val latest = values[key]) {
                    is String -> editor.putString(key, latest)
                    is Boolean -> editor.putBoolean(key, latest)
                    is Long -> editor.putLong(key, latest)
                }
                editor.commit()
            }
        }
    }
}

internal class PushStringPreference(private val key: String, private val default: String) : ReadWriteProperty<Any?, String?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = PushPreferences.value(key) as? String ?: default
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) = PushPreferences.put(key, value.orEmpty())
}
internal class PushBooleanPreference(private val key: String, private val default: Boolean) : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = PushPreferences.value(key) as? Boolean ?: default
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = PushPreferences.put(key, value)
}
internal class PushLongPreference(private val key: String, private val default: Long) : ReadWriteProperty<Any?, Long> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = PushPreferences.value(key) as? Long ?: default
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) = PushPreferences.put(key, value)
}
