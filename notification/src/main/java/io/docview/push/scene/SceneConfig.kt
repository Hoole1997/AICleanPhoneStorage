package io.docview.push.scene

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import io.docview.push.config.PushContentLanguage

internal object SceneConfig {
    const val MAX_JSON = 262_144
    fun objectValue(raw: String): JsonObject {
        require(raw.length <= MAX_JSON)
        return JsonReader(StringReader(raw)).use {
            it.strictness = Strictness.STRICT
            val result = JsonParser.parseReader(it).asJsonObject
            require(it.peek() == JsonToken.END_DOCUMENT)
            result
        }
    }
    fun policy(raw: String): ScenePolicy {
        val root = objectValue(raw)
        val switches = root.get("scenes")?.takeIf { it.isJsonObject }?.asJsonObject
        return ScenePolicy(boolean(root, "enabled", true), PushScene.entries.associateWith { scene ->
            val value = root.get(scene.intervalKey)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asString?.toIntOrNull()?.takeIf { it > 0 } ?: 10
            val item = switches?.get(scene.code.toString())?.takeIf { it.isJsonObject }?.asJsonObject
            SceneRule(item?.let { boolean(it, "enabled", true) } ?: true, value)
        })
    }
    fun boolean(root: JsonObject, key: String, fallback: Boolean) = root.get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: fallback
}

internal data class SceneCopy(val id: String, val text: String, val button: String,
    val condition: String = "any", val translations: Map<String, SceneCopyText> = emptyMap()) {
    fun text(language: String): SceneCopyText {
        // 与日龄推送共用地区匹配：例如 es→es-MX、hi→hi-IN；精确 es-ES 仍优先。
        // 只选择当前语言，不改变本场景文案游标，不改动旧推送的实现或数据。
        return PushContentLanguage.candidates(language).firstNotNullOfOrNull { translations[it.lowercase()] }
            ?: SceneCopyText(text, button)
    }
    fun applies(event: SceneEvent) = when (condition) {
        "any" -> true
        "full" -> event.fullCharge
        "not_full" -> !event.fullCharge
        "charging" -> event.charging == true
        "connected" -> event.plugged == true
        "not_full_unplugged" -> !event.fullCharge && event.plugged == false
        "percent_20" -> event.batteryPercent == 20
        "critical" -> event.batteryPercent?.let { it <= 10 } == true
        "mobile" -> event.network == SceneNetwork.MOBILE
        "wifi" -> event.network == SceneNetwork.WIFI
        else -> false
    }
}
internal data class SceneCopyText(val text: String, val button: String)
internal data class SceneSelection(val id: String, val text: SceneCopyText)

internal object SceneCopies {
    private val conditions = setOf("any", "full", "not_full", "charging", "connected", "not_full_unplugged", "percent_20", "critical", "mobile", "wifi")
    // Android ICU 对孤立的右花括号比 JVM 更严格，左右括号都按字面量转义。
    private val variable = Regex("\\{([a-z_]+)\\}")
    fun parse(raw: String, defaults: Map<PushScene, List<SceneCopy>> = emptyMap()): Map<PushScene, List<SceneCopy>> {
        val root = SceneConfig.objectValue(raw)
        val parsed = PushScene.entries.associateWith { scene ->
            val items = root.get(scene.code.toString())?.takeIf { it.isJsonArray }?.asJsonArray
            items?.take(16)?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val item = element.asJsonObject
                val id = string(item, "id", 80) ?: return@mapNotNull null
                val text = text(item) ?: return@mapNotNull null
                val condition = string(item, "condition", 30) ?: "any"
                if (condition !in conditions) return@mapNotNull null
                val translations = item.get("translations")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.take(32)
                    ?.mapNotNull { (tag, value) ->
                        if (!tag.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*"))) return@mapNotNull null
                        value.takeIf { it.isJsonObject }?.asJsonObject?.let(::text)?.let { tag.lowercase() to it }
                    }?.toMap().orEmpty()
                SceneCopy(id, text.text, text.button, condition, translations)
            }?.distinctBy { it.id }?.takeIf { it.isNotEmpty() } ?: defaults[scene].orEmpty()
        }
        require(parsed.values.any { it.isNotEmpty() })
        return parsed
    }

    private fun string(root: JsonObject, key: String, limit: Int) = root.get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?.takeIf { it.isNotBlank() && it.length <= limit }
    private fun text(root: JsonObject): SceneCopyText? {
        // 尚未上线的新协议只接受完整富文本 text，不保留 title/desc 双字段分支。
        val body = string(root, "text", 500) ?: return null
        return SceneCopyText(body, string(root, "buttonText", 80) ?: return null).takeIf {
            it.text.length <= 500 && visibleLength(it.text) in 1..130 &&
                !Regex("(?i)<(script|style|img|iframe|object|svg)\\b").containsMatchIn(it.text + it.button)
        }
    }

    private fun visibleLength(value: String) = value.replace(Regex("<[^>]*>"), "")
        .replace(Regex("&(?:[A-Za-z]+|#[0-9]+|#x[0-9A-Fa-f]+);"), "x").trim().length

    fun select(copies: List<SceneCopy>, lastId: String, event: SceneEvent, language: String): SceneSelection? {
        if (copies.isEmpty()) return null
        val previous = copies.indexOfFirst { it.id == lastId }
        val start = if (previous < 0) 0 else (previous + 1) % copies.size
        for (offset in copies.indices) {
            val copy = copies[(start + offset) % copies.size]
            if (!copy.applies(event)) continue
            val value = copy.text(language)
            val text = SceneCopyText(expand(value.text, event) ?: continue, expand(value.button, event) ?: continue)
            if (visibleLength(text.text) !in 1..130) continue
            return SceneSelection(copy.id, text)
        }
        return null
    }

    private fun expand(text: String, event: SceneEvent): String? {
        var missing = false
        val result = variable.replace(text) { match ->
            val value = when (match.groupValues[1]) {
                "battery_percent" -> event.batteryPercent?.toString()
                "app_name" -> event.displayName.takeIf { it.isNotBlank() }
                "file_count" -> event.fileCount.takeIf { it > 0 }?.toString()
                "size_mb" -> event.bytes.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it / 1_000_000.0) }
                else -> null
            }
            if (value == null) { missing = true; "" } else value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        }
        return result.takeUnless { missing || it.length > 500 || it.contains('{') || it.contains('}') }
    }
}
