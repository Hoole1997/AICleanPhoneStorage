package com.example.aicleanphonestorage.feature.filecleaner.ui

import com.example.aicleanphonestorage.core.ui.completion.CompletionKind
import com.example.aicleanphonestorage.core.ui.completion.CompletionReport
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationSummary

/** 压缩副本数与删除原图数不能相加；只在真实删除后展示净释放空间。 */
internal fun OperationSummary.completionReport(
    feature: CleanupFeature,
    operation: Long,
): CompletionReport {
    val compression = feature == CleanupFeature.PHOTO_COMPRESS
    return CompletionReport(
        kind = if (compression) CompletionKind.COMPRESSION else CompletionKind.CLEANUP,
        completed = if (compression) copied else deleted,
        failed = failed,
        skipped = skipped,
        freedBytes = freedBytes,
        reducedBytes = reducedBytes,
        originalsRemaining = if (compression) (copied - deleted).coerceAtLeast(0) else 0,
        removableOriginals = if (compression) originalsAvailable else 0,
        operationId = operation,
        emptyScan = feature == CleanupFeature.SMART_CLEAN && total == 0,
        inputBytes = inputBytes.takeIf { compression },
        copiedOriginalBytes = copiedOriginalBytes.takeIf { compression },
        outputBytes = outputBytes.takeIf { compression },
    )
}
