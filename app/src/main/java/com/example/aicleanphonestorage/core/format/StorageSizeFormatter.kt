package com.example.aicleanphonestorage.core.format

import android.content.Context
import com.example.aicleanphonestorage.R
import java.text.NumberFormat

/** 压缩前后与截图共用相同单位/精度；只格式化真实字节，不从编码质量推算文件体积。 */
internal class StorageSizeFormatter(private val context: Context) {
    private val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }
    fun megabytes(bytes: Long): String = context.getString(
        R.string.cleanup_megabytes, number.format(bytes.coerceAtLeast(0) / 1_000_000.0),
    )
}
