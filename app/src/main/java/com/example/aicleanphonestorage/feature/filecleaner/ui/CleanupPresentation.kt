package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.Context
import java.text.NumberFormat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

internal val CleanupFeature.titleRes: Int
    get() =
        when (this) {
            CleanupFeature.PHOTO_COMPRESS -> R.string.cleanup_photo
            CleanupFeature.LARGE_FILES -> R.string.cleanup_large
            CleanupFeature.UNUSED_FILES -> R.string.cleanup_unused
            CleanupFeature.SCREENSHOTS -> R.string.cleanup_screenshots
            CleanupFeature.SMART_CLEAN -> R.string.junk_title
        }

/** 页面级格式化器只由 View 渲染器持有，截图总量/已选容量与压缩计数共用本地化规则。 */
internal class CleanupValueFormatter(private val context: Context) {
    private val countFormat = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    private val sizes = com.example.aicleanphonestorage.core.format.StorageSizeFormatter(context)
    fun count(value: Int): String = countFormat.format(value)
    fun megabytes(bytes: Long): String = sizes.megabytes(bytes)
}
