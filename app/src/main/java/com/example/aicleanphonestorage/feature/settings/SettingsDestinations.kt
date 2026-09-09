package com.example.aicleanphonestorage.feature.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.annotation.StringRes

/** 外部地址集中配置，构造 Intent 与发起跳转分离；不嵌入 WebView、不自动发送或附带设备标识。 */
internal object SettingsDestinations {
    fun privacy(url: String): Intent? {
        val uri = Uri.parse(url.trim())
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return null
        return Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    fun feedback(email: String, subject: String, message: String): Intent? {
        val recipient = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches() || message.isBlank()) return null
        // 文本只作为 mailto 参数编码，换行/&/# 等不会改变收件人或 URI 结构。
        val uri =
            Uri.parse(
                "mailto:" +
                    Uri.encode(recipient, "@") +
                    "?subject=" +
                    Uri.encode(subject) +
                    "&body=" +
                    Uri.encode(message)
            )
        return Intent(Intent.ACTION_SENDTO, uri)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, message)
    }

    fun open(activity: Activity, intent: Intent?, @StringRes unavailable: Int): Boolean {
        if (intent != null)
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                /* 无浏览器/邮件客户端时保留当前页。 */
            } catch (_: SecurityException) {
                /* 工作资料/OEM 限制不尝试绕过。 */
            }
        Toast.makeText(activity, unavailable, Toast.LENGTH_LONG).show()
        return false
    }
}
