package com.example.aicleanphonestorage.core.ui.completion

internal enum class CompletionKind {
    CLEANUP,
    COMPRESSION,
    NOTIFICATIONS,
}

/** 只传已确认的摘要，不携带文件、URI、应用列表，也不在完成页重新执行操作。 */
internal data class CompletionReport(
    val kind: CompletionKind,
    val completed: Int,
    val failed: Int = 0,
    val skipped: Int = 0,
    val freedBytes: Long = 0,
    val reducedBytes: Long = 0,
    val originalsRemaining: Int = 0,
    val removableOriginals: Int = 0,
    val operationId: Long = 0,
    val inputBytes: Long? = null,
    val copiedOriginalBytes: Long? = null,
    val outputBytes: Long? = null,
    val emptyScan: Boolean = false,
) {
    val successful: Boolean
        get() = kind == CompletionKind.NOTIFICATIONS || emptyScan || completed > 0

    val partial: Boolean
        get() = successful && (failed > 0 || skipped > 0)

    val celebrate: Boolean
        get() = successful && !partial
}
