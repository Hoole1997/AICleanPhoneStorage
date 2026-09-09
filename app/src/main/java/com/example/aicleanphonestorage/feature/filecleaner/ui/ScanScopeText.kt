package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.content.Context
import com.example.aicleanphonestorage.R

/** 保持历史索引的范围标识不变，在展示层本地化；用户自定义目录名原样保留。 */
internal fun Context.scanScopeText(scope: String): String =
    when (scope) {
        "Shared storage" -> getString(R.string.cleanup_scope_shared)
        "Selected folder" -> getString(R.string.cleanup_scope_folder)
        "Photos" -> getString(R.string.cleanup_photos)
        "Selected photos" -> getString(R.string.cleanup_limited)
        else -> scope
    }
