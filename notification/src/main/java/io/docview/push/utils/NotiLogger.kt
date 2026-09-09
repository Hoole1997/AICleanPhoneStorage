package io.docview.push.utils

import android.util.Log
import io.docview.push.BuildConfig

/**
 * 广告日志工具类
 * 提供统一的日志输出控制和管理
 */
object NotiLogger {
    private const val TAG = "NotificationModule"

    /**
     * 日志开关，默认为true
     */
    private var isLogEnabled = BuildConfig.DEBUG

    /**
     * 设置日志开关
     * @param enabled 是否启用日志
     */
    fun setLogEnabled(enabled: Boolean) {
        isLogEnabled = enabled
    }

    /**
     * 获取日志开关状态
     * @return 是否启用日志
     */
    fun isLogEnabled(): Boolean = isLogEnabled

    /**
     * Debug日志
     * @param message 日志消息
     */
    fun d(message: String) {
        if (isLogEnabled) {
            Log.d(TAG, message)
        }
    }

    /**
     * Debug日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun d(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.d(TAG, message.format(*args))
        }
    }

    /**
     * Warning日志
     * @param message 日志消息
     */
    fun w(message: String) {
        if (isLogEnabled) {
            Log.w(TAG, message)
        }
    }

    /**
     * Warning日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun w(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.w(TAG, message.format(*args))
        }
    }

    /**
     * Error日志
     * @param message 日志消息
     */
    fun e(message: String) {
        if (isLogEnabled) {
            Log.e(TAG, message)
        }
    }

    /**
     * Error日志（带异常）
     * @param message 日志消息
     * @param throwable 异常对象
     */
    fun e(message: String, throwable: Throwable?) {
        if (isLogEnabled) {
            Log.e(TAG, message, throwable)
        }
    }

    /**
     * Error日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun e(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.e(TAG, message.format(*args))
        }
    }

    /**
     * Error日志（带参数和异常）
     * @param message 日志消息模板
     * @param throwable 异常对象
     * @param args 参数列表
     */
    fun e(message: String, throwable: Throwable?, vararg args: Any?) {
        if (isLogEnabled) {
            Log.e(TAG, message.format(*args), throwable)
        }
    }

    /**
     * Info日志
     * @param message 日志消息
     */
    fun i(message: String) {
        if (isLogEnabled) {
            Log.i(TAG, message)
        }
    }

    /**
     * Info日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun i(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.i(TAG, message.format(*args))
        }
    }

    /**
     * Verbose日志
     * @param message 日志消息
     */
    fun v(message: String) {
        if (isLogEnabled) {
            Log.v(TAG, message)
        }
    }

    /**
     * Verbose日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun v(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.v(TAG, message.format(*args))
        }
    }

    // ===== 内部辅助方法 =====

    private fun formatMessage(msg: String, prefix: String = ""): String {
        return if (prefix.isNotEmpty()) "$prefix: $msg" else msg
    }

    private fun shouldLog(level: Int): Boolean {
        return isLogEnabled && level >= Log.DEBUG
    }

    private fun appendTimestamp(msg: String): String {
        return "${System.currentTimeMillis()} - $msg"
    }

    @Suppress("unused")
    private const val MAX_LOG_LENGTH = 4000

    @Suppress("unused")
    private const val LOG_PREFIX = "[Push]"
}
