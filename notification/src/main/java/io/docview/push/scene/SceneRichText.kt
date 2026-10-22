package io.docview.push.scene

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
        val parsed = SpannableStringBuilder(HtmlCompat.fromHtml(safe, HtmlCompat.FROM_HTML_MODE_COMPACT))
        // 需求中的加粗内容同时标红，远程使用 b/strong 即可，无需另外配置同一颜色。
        parsed.getSpans(0, parsed.length, StyleSpan::class.java).forEach { style ->
            if (style.style and Typeface.BOLD != 0) parsed.setSpan(ForegroundColorSpan(Color.rgb(251, 73, 73)),
                parsed.getSpanStart(style), parsed.getSpanEnd(style), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return parsed
    }
    fun plain(value: String) = render(value).toString().trim()
}
