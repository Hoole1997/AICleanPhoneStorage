package io.docview.push.alarm

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigInteger

/** 独立于 Service/WorkManager 的有效调度策略；线上/缓存/本地默认值由配置解析层统一选择。 */
data class AlarmWakeupPolicy(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 1_440
    }
}

data class AlarmWakeupConfig(
    val paidTier: AlarmWakeupPolicy = AlarmWakeupPolicy(),
    val standardTier: AlarmWakeupPolicy = AlarmWakeupPolicy(),
    val enabled: Boolean = false,
) {
    /** 只有已确认的买量归因使用 paid；未确认状态不能借构建时默认渠道开启唤醒。 */
    fun policyFor(confirmedPaid: Boolean): AlarmWakeupPolicy {
        val tier = if (confirmedPaid) paidTier else standardTier
        return tier.copy(enabled = enabled && tier.enabled)
    }
}

private const val MAX_CONFIG_LENGTH = 16_384
private val INTEGER_TOKEN = Regex("-?(0|[1-9][0-9]*)")

/**
 * 按需求的 alarm_wakeup_config 独立协议读取，不复用普通推送或 Service 的间隔配置。
 * 两个 tier 必须完整有效；根级 alarm_wakeup_enabled 是总开关，旧线上 JSON 缺失时兼容为开启。
 * 无效配置返回禁用占位值；实际运行通过 resolveAlarmWakeupConfig 回退有效缓存/本地默认 JSON。
 * 使用严格流式读取，拒绝数字字符串、小数、重复字段和尾随 JSON，避免模糊配置激活后台任务。
 */
fun parseAlarmWakeupConfig(json: String): AlarmWakeupConfig =
    parseAlarmWakeupConfigOrNull(json) ?: AlarmWakeupConfig()

internal fun parseAlarmWakeupConfigOrNull(json: String): AlarmWakeupConfig? {
    if (json.isBlank() || json.length > MAX_CONFIG_LENGTH) return null
    return try {
        JsonReader(StringReader(json)).use { reader ->
            reader.isLenient = false
            reader.beginObject()
            var paid: AlarmWakeupPolicy? = null
            var standard: AlarmWakeupPolicy? = null
            var enabled: Boolean? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "alarm_wakeup_enabled" -> {
                        require(enabled == null) { "Repeated master switch" }
                        enabled = reader.readSwitch()
                    }
                    "paid_tier" -> {
                        require(paid == null) { "Repeated paid tier" }
                        paid = reader.readPolicy()
                    }
                    "standard_tier" -> {
                        require(standard == null) { "Repeated standard tier" }
                        standard = reader.readPolicy()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing configuration" }
            AlarmWakeupConfig(requireNotNull(paid), requireNotNull(standard), enabled ?: true)
        }
    } catch (_: Exception) {
        // 区分无效参数与有效的显式关闭；前者可以回退，后者必须保留，且不记录线上原文。
        null
    }
}

private fun JsonReader.readPolicy(): AlarmWakeupPolicy {
    beginObject()
    var enabled: Boolean? = null
    var interval: Int? = null
    while (hasNext()) {
        when (nextName()) {
            "alarm_wakeup_enabled" -> {
                require(enabled == null) { "Repeated enabled value" }
                enabled = readSwitch()
            }
            "service_heartbeat_interval" -> {
                require(interval == null) { "Repeated interval" }
                // 先按整数验证再限幅，极大数值也不能溢出成负间隔或零间隔。
                interval = readInteger()
                    .max(BigInteger.valueOf(AlarmWakeupPolicy.MIN_INTERVAL_MINUTES.toLong()))
                    .min(BigInteger.valueOf(AlarmWakeupPolicy.MAX_INTERVAL_MINUTES.toLong()))
                    .toInt()
            }
            else -> skipValue()
        }
    }
    endObject()
    return AlarmWakeupPolicy(requireNotNull(enabled), interval ?: AlarmWakeupPolicy.DEFAULT_INTERVAL_MINUTES)
}

private fun JsonReader.readSwitch(): Boolean = when (readInteger()) {
    BigInteger.ZERO -> false
    BigInteger.ONE -> true
    else -> throw IllegalArgumentException("Invalid enabled value")
}

/** 仅有效线上值允许写入缓存，缺失/读取失败不能覆盖先前的显式关闭或调度策略。 */
internal data class AlarmWakeupConfigResolution(val config: AlarmWakeupConfig, val remoteJson: String? = null)

internal fun resolveAlarmWakeupConfig(
    remoteJson: String?,
    cachedJson: String,
    defaultJson: String,
): AlarmWakeupConfigResolution {
    remoteJson?.let { json ->
        parseAlarmWakeupConfigOrNull(json)?.let { return AlarmWakeupConfigResolution(it, json) }
    }
    val effective = parseAlarmWakeupConfigOrNull(cachedJson)
        ?: parseAlarmWakeupConfig(defaultJson)
    return AlarmWakeupConfigResolution(effective)
}

private fun JsonReader.readInteger(): BigInteger {
    require(peek() == JsonToken.NUMBER) { "Expected integer number" }
    val token = nextString()
    require(INTEGER_TOKEN.matches(token)) { "Expected integer token" }
    return token.toBigInteger()
}
