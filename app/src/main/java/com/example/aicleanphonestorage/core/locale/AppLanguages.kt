package com.example.aicleanphonestorage.core.locale

import java.util.Locale

/** BCP-47 标签与 Android 资源目录配套；语言用本族语显示，切换后仍能识别自己的语言。 */
internal data class AppLanguage(val tag: String, val nativeName: String)

internal object AppLanguages {
    val supported =
        listOf(
            AppLanguage("en", "English"),
            AppLanguage("zh-Hans", "简体中文"),
            AppLanguage("hi", "हिन्दी"),
            AppLanguage("es", "Español"),
            AppLanguage("ar", "العربية"),
            AppLanguage("pt-BR", "Português (Brasil)"),
            AppLanguage("bn", "বাংলা"),
            AppLanguage("ur", "اردو"),
            AppLanguage("id", "Bahasa Indonesia"),
            AppLanguage("ru", "Русский"),
            AppLanguage("fr", "Français"),
            AppLanguage("de", "Deutsch"),
            AppLanguage("ja", "日本語"),
            AppLanguage("ko", "한국어"),
            AppLanguage("vi", "Tiếng Việt"),
            AppLanguage("tr", "Türkçe"),
        )

    fun valid(tag: String) = tag.isEmpty() || supported.any { it.tag == tag }

    fun matching(tag: String): String {
        if (tag.isBlank()) return ""
        supported
            .firstOrNull { it.tag.equals(tag, true) }
            ?.let {
                return it.tag
            }
        val locale = Locale.forLanguageTag(tag)
        return supported
            .firstOrNull { Locale.forLanguageTag(it.tag).language == locale.language }
            ?.tag
            .orEmpty()
    }
}
