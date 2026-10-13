package com.example.aicleanphonestorage.app.risk

import android.util.Log
import net.corekit.core.report.ReportDataManager
import java.lang.reflect.Array as ReflectArray
import java.util.IdentityHashMap

/**
 * XM 遥测出口。复用 metrics 已注册的数数实例及公共参数，
 * 不广播给其他 Reporter；调用方须在启动 XM/广告前完成 Reporter 注册。
 */
internal class DeviceRiskTelemetry(
    private val eventSink: (String, Map<String, Any>) -> Unit = { name, parameters ->
        ReportDataManager.reportDataByName("ThinkingData", name, parameters)
    },
    private val reportFailure: (String) -> Unit = { name ->
        // 只记录事件名，不把指纹、token、deviceId 或远端原始响应写入日志。
        Log.w("DeviceRiskTelemetry", "Risk event delivery failed: $name")
    },
) {
    fun reportXmEvent(name: String, params: Map<String, *>) {
        if (name.length > XmEventParameters.MAX_NAME_LENGTH) return
        val eventName = name.trim().takeIf(String::isNotEmpty) ?: return
        try {
            emit(eventName, XmEventParameters.normalize(params))
        } catch (_: Exception) {
            // SDK 参数容器也可能在遍历时抛异常；遥测不能中断 XM 检测结果的处理。
            safelyReportFailure(eventName)
        }
    }

    private fun emit(name: String, params: Map<String, Any>) {
        try {
            eventSink(name, params)
        } catch (_: Exception) {
            safelyReportFailure(name)
        }
    }

    private fun safelyReportFailure(name: String) {
        try { reportFailure(name) } catch (_: Exception) { /* 日志失败也不得影响业务。 */ }
    }

}

/**
 * 把 SDK 动态属性收敛为有界 JSON 兼容树。ThinkingReporter 已用 Gson 将 Map/List 转成
 * JSON object/array，因此不传 JSONObject，避免 Gson 反射其内部字段。每次事件使用独立
 * 预算与循环检测，既不持有 SDK 原始容器，也不在广告/SDK 回调线程展开无限数据。
 */
internal object XmEventParameters {
    const val MAX_NAME_LENGTH = 64
    const val MAX_KEY_LENGTH = 64
    const val MAX_STRING_LENGTH = 2_048
    const val MAX_CONTAINER_ENTRIES = 32
    const val MAX_DEPTH = 4
    const val MAX_NODES = 256
    const val MAX_TEXT_LENGTH = 16_384

    fun normalize(parameters: Map<String, *>): Map<String, Any> =
        Normalizer().map(parameters, depth = 0).orEmpty()

    private class Normalizer {
        private var remainingNodes = MAX_NODES
        private var remainingText = MAX_TEXT_LENGTH
        private val activeContainers = IdentityHashMap<Any, Boolean>()

        fun map(source: Map<*, *>, depth: Int): Map<String, Any>? =
            container(source, depth) {
                val result = linkedMapOf<String, Any>()
                val iterator = source.entries.iterator()
                var visited = 0
                while (visited++ < MAX_CONTAINER_ENTRIES && remainingNodes > 0 && iterator.hasNext()) {
                    val entry = iterator.next()
                    val key = (entry.key as? String)?.takeIf {
                        it.length <= MAX_KEY_LENGTH && it.length <= remainingText && it.isNotBlank()
                    } ?: continue
                    remainingText -= key.length
                    value(entry.value, depth + 1)?.let { result[key] = it }
                }
                result.takeIf { it.isNotEmpty() }
            }

        private fun value(source: Any?, depth: Int): Any? {
            if (source == null || remainingNodes-- <= 0) return null
            return when (source) {
                is String -> {
                    val limit = minOf(MAX_STRING_LENGTH, remainingText)
                    val text = source.take(limit).takeIf { it.isNotBlank() } ?: return null
                    remainingText -= text.length
                    text
                }
                is Boolean, is Byte, is Short, is Int, is Long -> source
                is Float -> source.takeIf(Float::isFinite)
                is Double -> source.takeIf(Double::isFinite)
                is Map<*, *> -> map(source, depth)
                is Iterable<*> -> container(source, depth) {
                    val iterator = source.iterator()
                    val result = mutableListOf<Any>()
                    var visited = 0
                    while (visited++ < MAX_CONTAINER_ENTRIES && remainingNodes > 0 && iterator.hasNext()) {
                        value(iterator.next(), depth + 1)?.let(result::add)
                    }
                    result.takeIf { it.isNotEmpty() }
                }
                else -> if (source.javaClass.isArray) array(source, depth) else null
            }
        }

        private fun array(source: Any, depth: Int): List<Any>? = container(source, depth) {
            val result = mutableListOf<Any>()
            val length = minOf(ReflectArray.getLength(source), MAX_CONTAINER_ENTRIES)
            for (index in 0 until length) {
                if (remainingNodes <= 0) break
                value(ReflectArray.get(source, index), depth + 1)?.let(result::add)
            }
            result.takeIf { it.isNotEmpty() }
        }

        private inline fun <T> container(source: Any, depth: Int, block: () -> T?): T? {
            if (depth > MAX_DEPTH || activeContainers.put(source, true) != null) return null
            return try { block() } finally { activeContainers.remove(source) }
        }
    }
}
