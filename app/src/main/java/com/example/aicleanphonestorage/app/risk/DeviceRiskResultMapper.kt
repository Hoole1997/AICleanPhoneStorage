package com.example.aicleanphonestorage.app.risk

import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/**
 * SDK 0.0.5 会把 HTTP 失败和有效空 deviceId 都表示为远端异常；仅布尔值不足以决定拦截。
 * 本产品 SDK 随包测试确认成功协议为 code=200/data 对象，空 ID 的诊断为“deviceId 缺失”。
 * 无法确认完成的检测（网络/超时/解析/未知响应）整体放行，不伪造安全结果。
 */
internal object DeviceRiskResultMapper {
    fun map(
        isVpn: Boolean,
        isDnsAbnormal: Boolean,
        isIpAbnormal: Boolean,
        isSimulator: Boolean,
        isAdb: Boolean,
        isNoSim: Boolean,
        isNoGp: Boolean,
        isSys9Low: Boolean,
        isRemoteAbnormal: Boolean,
        deviceId: String,
        errorMessage: String,
        rawResponse: String,
        integrityUnavailable: Boolean = false,
    ): DeviceRiskSnapshot {
        val error = errorMessage.trim()
        val knownEmptyId = error.isEmpty() || error == MISSING_DEVICE_ID
        if (integrityUnavailable || !knownEmptyId || (error.isNotEmpty() && deviceId.isNotBlank()) ||
            (deviceId.isBlank() && !validEmptyDeviceIdResponse(rawResponse))) {
            throw DeviceRiskUnavailableException(DeviceRiskUnavailableReason.SDK_ERROR)
        }
        return DeviceRiskSnapshot(buildSet {
            if (isVpn) add(DeviceRiskSignal.VPN)
            if (isDnsAbnormal) add(DeviceRiskSignal.DNS)
            if (isIpAbnormal) add(DeviceRiskSignal.IP)
            if (isSimulator) add(DeviceRiskSignal.SIMULATOR)
            if (isAdb) add(DeviceRiskSignal.ADB)
            if (isNoSim) add(DeviceRiskSignal.NO_SIM)
            if (isNoGp) add(DeviceRiskSignal.NO_GP)
            if (isSys9Low) add(DeviceRiskSignal.SYS_9_LOW)
            if (isRemoteAbnormal && deviceId.isBlank()) add(DeviceRiskSignal.PLAY)
        })
    }

    private fun validEmptyDeviceIdResponse(raw: String): Boolean {
        if (raw.isBlank() || raw.length > MAX_RESPONSE_CHARS || !hasBoundedNesting(raw)) return false
        return try {
            val parsed = JsonReader(StringReader(raw)).use { reader ->
                reader.strictness = Strictness.STRICT
                val value = JsonParser.parseReader(reader)
                if (reader.peek() != JsonToken.END_DOCUMENT) return false
                value
            }
            if (!parsed.isJsonObject) return false
            val root = parsed.asJsonObject
            val code = root.get("code")
            if (code == null || !code.isJsonPrimitive || code.asString != "200") return false
            val data = root.get("data")
            if (data == null || !data.isJsonObject) return false
            val id = data.asJsonObject.get("deviceId")
            id == null || id.isJsonNull || (id.isJsonPrimitive && id.asJsonPrimitive.isString && id.asString.isBlank())
        } catch (_: Exception) {
            false
        }
    }

    /** 在解析前限制嵌套，避免第三方响应导致递归解析堆栈或内存放大；不复制原文。 */
    private fun hasBoundedNesting(raw: String): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > MAX_DEPTH) return false
                '}', ']' -> if (--depth < 0) return false
            }
        }
        return depth == 0 && !quoted
    }

    private const val MISSING_DEVICE_ID = "deviceId 缺失"
    private const val MAX_RESPONSE_CHARS = 65_536
    private const val MAX_DEPTH = 8
}
