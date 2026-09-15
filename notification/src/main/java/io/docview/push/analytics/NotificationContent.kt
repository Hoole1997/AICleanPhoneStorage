package io.docview.push.analytics

/** 只保留本 App 通知用于埋点的有界文案，不携带 RemoteViews、Bitmap 或其他应用通知数据。 */
data class NotificationContent(val title: String, val text: String) {
    fun bounded() = NotificationContent(clipped(title, 120), clipped(text, 500))

    private fun clipped(value: String, limit: Int): String {
        if (value.length <= limit) return value
        // 只检查截断边界，不扫描可能很长的外部字符串，也不把 emoji 代理对切开。
        val end = if (Character.isHighSurrogate(value[limit - 1]) && Character.isLowSurrogate(value[limit])) limit - 1 else limit
        return value.substring(0, end)
    }

    fun properties(fromBackground: Boolean): Map<String, Any> = bounded().let {
        mapOf(
            // Firebase 事件参数不接受 Boolean；true/false 按文档取值以字符串跨 SDK 传递。
            "from_background" to fromBackground.toString(),
            "title" to it.title,
            "text" to it.text,
        )
    }
}
