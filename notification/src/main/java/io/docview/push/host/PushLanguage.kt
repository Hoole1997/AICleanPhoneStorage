package io.docview.push.host

/** 可选地震文案沿用来源语言码，宿主只暴露当前 Locale，不导入浏览器语言控制器。 */
internal class PushLanguage {
    fun getAliens() = PushEnvironment.context.resources.configuration.locales[0].language
    companion object {
        private val instance = PushLanguage()
        fun getInstance() = instance
        const val ARABIC = "ar"
        const val CHINESE_CN = "zh-CN"
        const val CHINESE_HK = "zh-HK"
        const val CHINESE_MO = "zh-MO"
        const val CHINESE_TW = "zh-TW"
        const val DANISH = "da"
        const val ENGLISH = "en"
        const val FRENCH = "fr"
        const val GERMAN = "de"
        const val HINDI = "hi"
        const val INDONESIAN = "id"
        const val ITALIAN = "it"
        const val JAPANESE = "ja"
        const val KOREAN = "ko"
        const val PERSIAN = "fa"
        const val PORTUGUESE = "pt"
        const val RUSSIAN = "ru"
        const val SPANISH = "es"
        const val SWEDISH = "sv"
        const val THAI = "th"
        const val TURKISH = "tr"
        const val VIETNAMESE = "vi"
    }
}
