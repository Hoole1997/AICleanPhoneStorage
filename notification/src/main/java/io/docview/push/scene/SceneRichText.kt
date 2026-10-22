package io.docview.push.scene

import androidx.core.text.HtmlCompat

/** RemoteViews 支持的有限富文本，不使用 WebView、远程图片、链接或可执行 HTML。 */
internal object SceneRichText {
    fun render(value: String): CharSequence {
        val safe = value.take(500)
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?is)<[^>]*>")) { match ->
                val tag = match.value
                if (tag.matches(Regex("(?i)</?(b|strong|i|em|u)>|<br\\s*/?>|</font>|<font\\s+color=[\"']#[0-9a-f]{6}[\"']\\s*>"))) tag else ""
            }
        // b/strong 仅加粗；颜色只来自 HTML font 标签，不覆盖远程指定颜色或添加默认颜色。
        return HtmlCompat.fromHtml(safe, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
    fun plain(value: String) = render(value).toString().trim()
}
