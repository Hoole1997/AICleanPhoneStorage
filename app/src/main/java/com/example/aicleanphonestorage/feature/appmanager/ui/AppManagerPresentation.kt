package com.example.aicleanphonestorage.feature.appmanager.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.appmanager.data.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date

/** 格式化器只供界面主线程使用；缺少历史记录不等于从未使用。 */
internal class AppManagerPresentation(private val context: Context) {
    private val date = SimpleDateFormat("dd/MM/yyyy", context.resources.configuration.locales[0])
    private val dateTime =
        SimpleDateFormat("dd/MM/yyyy HH:mm", context.resources.configuration.locales[0])
    private val numbers =
        NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 2
        }
    private val units = context.resources.getStringArray(R.array.app_manager_size_units)

    private fun bytes(value: Long): String {
        var amount = value.toDouble()
        var unit = 0
        while (amount >= 1000 && unit < units.lastIndex) {
            amount /= 1000
            unit++
        }
        return context.getString(
            R.string.app_manager_size_value,
            numbers.format(amount),
            units[unit],
        )
    }

    fun installed(app: ManagedApp): String =
        context.getString(
            R.string.app_manager_installed,
            app.installedAt?.let { date.format(Date(it)) } ?: unknown(),
        )

    fun size(app: ManagedApp): CharSequence {
        val value = app.sizeBytes?.let { bytes(it) } ?: unknown()
        val label =
            when (app.sizeKind) {
                AppSizeKind.USED -> R.string.app_manager_size_used
                AppSizeKind.SHARED_UID -> R.string.app_manager_size_shared
                AppSizeKind.APK -> R.string.app_manager_size_apk
            }
        val text = context.getString(label, value)
        return SpannableString(text).apply {
            val start = text.indexOf(value)
            if (start >= 0)
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + value.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
        }
    }

    fun used(app: ManagedApp): CharSequence {
        // Never Used 是需求中的未知历史占位；数据层仍保留 Unavailable/NoRecentRecord，不伪造使用时间。
        val value = when (val usage = app.lastUse) {
            is AppLastUse.Recorded -> dateTime.format(Date(usage.timeMillis))
            else -> context.getString(R.string.app_manager_never_used)
        }
        val label = context.getString(R.string.app_manager_last_used, value)
        return SpannableString(label).apply {
            val start = label.indexOf(value)
            if (start >= 0) setSpan(android.text.style.ForegroundColorSpan(
                androidx.core.content.ContextCompat.getColor(context, R.color.traffic_blue)),
                start, start + value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun unknown() = context.getString(R.string.app_manager_unknown)
}
