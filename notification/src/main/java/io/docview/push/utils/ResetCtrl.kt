package io.docview.push.utils

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * 0点重置控制器
 * 用于管理需要在0点重置的数据，支持持久化和自动重置
 */
class ResetCtrl private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: ResetCtrl? = null

        fun getInstance(): ResetCtrl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResetCtrl().also { INSTANCE = it }
            }
        }
    }

    private var sharedPreferences: SharedPreferences? = null
    private var context: Context? = null

    /**
     * 初始化
     * @param context 上下文
     */
    fun initialize(context: Context) {
        if (sharedPreferences != null) {
            Logger.d("ResetCtrl 已经初始化")
            return
        }

        this.context = context.applicationContext
        sharedPreferences = context.getSharedPreferences("reset_at_midnight_prefs", Context.MODE_PRIVATE)
        Logger.d("ResetCtrl 初始化完成")
    }

    /**
     * 获取长整型值（带0点重置逻辑）
     * @param key 键名
     * @param defaultValue 默认值
     * @param enableMidnightReset 是否启用0点重置
     * @return 当前值
     */
    fun getLongValue(key: String, defaultValue: Long = 0L, enableMidnightReset: Boolean = true): Long {
        val prefs = sharedPreferences ?: return defaultValue

        if (enableMidnightReset) {
            val today = LocalDate.now().toString()
            val lastDateKey = "${key}_date"
            val lastDate = prefs.getString(lastDateKey, "")
            val lastValue = prefs.getLong(key, defaultValue)

            // 如果是新的一天，重置为默认值
            if (lastDate != today) {
                prefs.edit()
                    .putString(lastDateKey, today)
                    .putLong(key, defaultValue)
                    .apply()
                Logger.d("新的一天，重置 $key: $defaultValue")
                return defaultValue
            }

            return lastValue
        } else {
            // 不启用0点重置，直接返回存储的值
            return prefs.getLong(key, defaultValue)
        }
    }

    /**
     * 设置长整型值
     * @param key 键名
     * @param value 值
     * @param enableMidnightReset 是否启用0点重置
     */
    fun setLongValue(key: String, value: Long, enableMidnightReset: Boolean = true) {
        val prefs = sharedPreferences ?: return

        if (enableMidnightReset) {
            val today = LocalDate.now().toString()
            val lastDateKey = "${key}_date"

            prefs.edit()
                .putString(lastDateKey, today)
                .putLong(key, value)
                .apply()

            Logger.d("设置 $key: $value (启用0点重置)")
        } else {
            prefs.edit()
                .putLong(key, value)
                .apply()

            Logger.d("设置 $key: $value (不启用0点重置)")
        }
    }

    /**
     * 获取整型值（带0点重置逻辑）
     * @param key 键名
     * @param defaultValue 默认值
     * @param enableMidnightReset 是否启用0点重置
     * @return 当前值
     */
    fun getIntValue(key: String, defaultValue: Int = 0, enableMidnightReset: Boolean = true): Int {
        return getLongValue(key, defaultValue.toLong(), enableMidnightReset).toInt()
    }

    /**
     * 设置整型值
     * @param key 键名
     * @param value 值
     * @param enableMidnightReset 是否启用0点重置
     */
    fun setIntValue(key: String, value: Int, enableMidnightReset: Boolean = true) {
        setLongValue(key, value.toLong(), enableMidnightReset)
    }

    /**
     * 增加长整型值（原子操作）
     * @param key 键名
     * @param increment 增量
     * @param enableMidnightReset 是否启用0点重置
     * @return 增加后的值
     */
    fun incrementLongValue(key: String, increment: Long = 1L, enableMidnightReset: Boolean = true): Long {
        val currentValue = getLongValue(key, 0L, enableMidnightReset)
        val newValue = currentValue + increment
        setLongValue(key, newValue, enableMidnightReset)
        return newValue
    }

    /**
     * 增加整型值（原子操作）
     * @param key 键名
     * @param increment 增量
     * @param enableMidnightReset 是否启用0点重置
     * @return 增加后的值
     */
    fun incrementIntValue(key: String, increment: Int = 1, enableMidnightReset: Boolean = true): Int {
        return incrementLongValue(key, increment.toLong(), enableMidnightReset).toInt()
    }

    /**
     * 重置指定键的值
     * @param key 键名
     * @param defaultValue 默认值
     * @param enableMidnightReset 是否启用0点重置
     */
    fun resetValue(key: String, defaultValue: Long = 0L, enableMidnightReset: Boolean = true) {
        setLongValue(key, defaultValue, enableMidnightReset)
        Logger.d("手动重置 $key: $defaultValue (启用0点重置: $enableMidnightReset)")
    }

    /**
     * 检查是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return sharedPreferences != null
    }

    /**
     * 获取所有键的当前状态
     * @return 状态信息字符串
     */
    fun getStatus(): String {
        val prefs = sharedPreferences ?: return "未初始化"

        val today = LocalDate.now().toString()
        val allPrefs = prefs.all

        return buildString {
            appendLine("ResetCtrl 状态:")
            appendLine("当前日期: $today")
            appendLine("已存储的键:")

            allPrefs.forEach { (key, value) ->
                if (!key.endsWith("_date")) {
                    val dateKey = "${key}_date"
                    val date = prefs.getString(dateKey, "未知")
                    appendLine("  $key: $value (日期: $date)")
                }
            }
        }
    }
}
