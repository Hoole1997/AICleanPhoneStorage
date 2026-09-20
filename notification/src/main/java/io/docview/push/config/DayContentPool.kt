package io.docview.push.config

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

// 每个日池独立设上限；D6 是最多五个有界日池的组合，不能再截成单池的 256 条。
internal const val MAX_DAY_POOL_CHARS = 262_144
internal const val MAX_DAY_POOL_BYTES = 524_288

internal data class DayContentPool(val day: Int, val contents: List<Content>) {
    companion object {
        val days = 1..5
        fun key(day: Int): String {
            require(day in days)
            return "pushContentD${day}Json"
        }
    }
}

/** 本地文件、远程值和已缓存远程值共用协议校验；坏配置不能覆盖有效池。 */
internal fun parseDayContentPool(json: String, expectedDay: Int): DayContentPool {
    fun invalid(): Nothing = throw JsonSyntaxException("D$expectedDay 文案池协议校验失败")
    if (json.length > MAX_DAY_POOL_CHARS || json.toByteArray(Charsets.UTF_8).size > MAX_DAY_POOL_BYTES) invalid()
    val root = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: invalid()
    if (root["schemaVersion"]?.asString != "2" || root["pool"]?.asString != "D$expectedDay" ||
        root["defaultLanguage"]?.asString != "en") invalid()
    val contents = root["contents"]?.takeIf { it.isJsonArray }?.asJsonArray ?: invalid()
    if (contents.size() !in 1..MAX_PUSH_CONTENTS) invalid()
    val parsed = parsePushContents(contents.toString())
    if (parsed.map { it.id }.toSet().size != parsed.size) invalid()
    return DayContentPool(expectedDay, parsed)
}

/** 单池独立回退；远程为空、错误或池标识不匹配时，不覆盖有效缓存。 */
internal fun resolveDayContentPool(
    day: Int,
    remoteJson: String,
    current: DayContentPool?,
    cached: () -> String?,
    local: () -> String,
    cacheRemote: (String) -> Unit,
    log: (String) -> Unit = {},
): DayContentPool {
    fun selected(pool: DayContentPool, source: String): DayContentPool {
        log("[推送日池] 配置选用：日池=D$day，远程参数=${DayContentPool.key(day)}，来源=$source，条数=${pool.contents.size}")
        return pool
    }
    val remote = remoteJson.takeIf { it.isNotBlank() }?.let { runCatching { parseDayContentPool(it, day) }.getOrNull() }
    if (remote != null) {
        cacheRemote(remoteJson)
        return selected(remote, "远程配置")
    }
    log("[推送日池] 配置回退：日池=D$day，原因=" +
        if (remoteJson.isBlank()) "远程值为空或不可用，检查已有文案池" else "远程格式或协议校验失败，保留有效文案池")
    if (current != null) return selected(current, "本进程有效池")
    val cachedJson = cached()
    val saved = cachedJson?.let { runCatching { parseDayContentPool(it, day) }.getOrNull() }
    if (saved != null) return selected(saved, "上次有效远程缓存")
    if (cachedJson != null) log("[推送日池] 缓存无效：日池=D$day，改用本地默认")
    return selected(parseDayContentPool(local(), day), "本地默认")
}

/** 纯内存快照一次替换五池；D6 按 D1→D5、各池原始顺序拼接，不复制翻译为新条目。 */
internal class DayContentCatalog(pools: Map<Int, DayContentPool>) {
    private val daily = DayContentPool.days.associateWith { requireNotNull(pools[it]).contents }
    private val combined = DayContentPool.days.flatMap { daily.getValue(it) }
    fun contents(day: Int): List<Content> = if (day == 6) combined else daily.getValue(day)
}

/** 只决定内容与循环游标，不改变发送资格、事件触发、频次或通知发布逻辑。 */
internal class DayContentRotation(
    private val firstLaunchEpochDay: Long,
    private val readIndex: (Int) -> Int,
    private val writeIndex: (Int, Int) -> Unit,
    private val log: (String) -> Unit = {},
) {
    fun poolDay(todayEpochDay: Long): Int = (todayEpochDay - firstLaunchEpochDay).coerceIn(0, 5).toInt() + 1

    @Synchronized fun next(catalog: DayContentCatalog, todayEpochDay: Long, languageTag: String): Content {
        val day = poolDay(todayEpochDay)
        val contents = catalog.contents(day)
        val index = readIndex(day).coerceAtLeast(0) % contents.size
        val nextIndex = (index + 1) % contents.size
        writeIndex(day, nextIndex)
        val item = contents[index]
        val language = item.matchedTranslationTag(languageTag)
        val normalized = PushContentLanguage.normalize(languageTag)
        val match = when {
            language != null && language == normalized -> "精确匹配译文"
            language != null -> "匹配同语言或区域译文"
            normalized?.substringBefore('-') == "en" -> "使用默认英文"
            else -> "缺少完整匹配译文，回退英文"
        }
        // 只输出有界诊断字段，不打印完整 JSON、通知正文或用户信息；文案选中不等于发送成功。
        val id = item.id.take(100).map { if (it.isISOControl()) ' ' else it }.joinToString("")
        log("[推送日池] 文案选中：实际日龄=${(todayEpochDay - firstLaunchEpochDay).coerceAtLeast(0) + 1}，" +
            "所选池=${if (day == 6) "D6+合并池（D1→D5）" else "D$day"}，" +
            "序号=${index + 1}/${contents.size}，文案编号=$id，动作编号=${item.actionType}，" +
            "应用语言=${normalized ?: "未识别"}，文案语言=${language ?: "en"}，语言处理=$match，" +
            "下次序号=${nextIndex + 1}，${if (nextIndex == 0) "本轮结束，下次从首条循环" else "继续顺序轮播"}")
        return item.localized(languageTag)
    }
}
