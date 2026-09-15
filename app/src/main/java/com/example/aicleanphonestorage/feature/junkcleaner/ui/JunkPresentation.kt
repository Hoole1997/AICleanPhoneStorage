package com.example.aicleanphonestorage.feature.junkcleaner.ui

import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind

internal val JunkKind.titleRes: Int
    get() =
        when (this) {
            JunkKind.INSTALLERS -> R.string.junk_installers
            JunkKind.TEMPORARY -> R.string.junk_temporary
            JunkKind.OLD_LOGS -> R.string.junk_logs
            JunkKind.EMPTY_FILES -> R.string.junk_empty_files
            JunkKind.EMPTY_FOLDERS -> R.string.junk_empty_folders
            JunkKind.AD_FILES -> R.string.junk_ad_files
            JunkKind.DUPLICATES -> R.string.junk_duplicates
            JunkKind.SIMILAR -> R.string.junk_similar
            JunkKind.REVIEW_QUALITY -> R.string.junk_quality
        }
internal val JunkKind.descriptionRes: Int
    get() =
        when (this) {
            JunkKind.INSTALLERS -> R.string.junk_installers_description
            JunkKind.TEMPORARY -> R.string.junk_temporary_files_description
            JunkKind.OLD_LOGS -> R.string.junk_logs_description
            JunkKind.EMPTY_FILES -> R.string.junk_empty_description
            JunkKind.EMPTY_FOLDERS -> R.string.junk_empty_folders_description
            JunkKind.AD_FILES -> R.string.junk_ad_files_description
            JunkKind.DUPLICATES -> R.string.junk_duplicate_description
            JunkKind.SIMILAR -> R.string.junk_similar_description
            JunkKind.REVIEW_QUALITY -> R.string.junk_quality_description
        }
