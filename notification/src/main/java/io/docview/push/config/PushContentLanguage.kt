package io.docview.push.config

import java.util.Locale

/** APP 语言按 BCP-47 精确匹配；无地区时采用需求文件的区域版本，不轮播不同语言。 */
internal object PushContentLanguage {
    private val supported = setOf("en", "zh", "hi", "es", "ar", "pt", "bn", "ur", "id", "ru", "fr", "de", "ja", "ko", "vi", "tr")
    private val regionalDefaults = mapOf("pt" to "pt-BR", "es" to "es-MX", "id" to "id-ID",
        "hi" to "hi-IN", "ja" to "ja-JP", "ko" to "ko-KR", "zh" to "zh-Hans")

    fun normalize(tag: String): String? {
        if (tag.length !in 2..35 || !tag.matches(Regex("[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*"))) return null
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        return locale.toLanguageTag().takeIf { locale.language in supported }
    }

    fun candidates(tag: String): List<String> {
        val exact = normalize(tag) ?: return emptyList()
        val language = Locale.forLanguageTag(exact).language
        return listOfNotNull(exact, language, regionalDefaults[language]).distinct()
    }
}
