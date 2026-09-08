package com.example.aicleanphonestorage.feature.filecleaner.ui

import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature

internal val CleanupFeature.titleRes: Int
    get() =
        when (this) {
            CleanupFeature.PHOTO_COMPRESS -> R.string.cleanup_photo
            CleanupFeature.LARGE_FILES -> R.string.cleanup_large
            CleanupFeature.UNUSED_FILES -> R.string.cleanup_unused
            CleanupFeature.SCREENSHOTS -> R.string.cleanup_screenshots
        }
