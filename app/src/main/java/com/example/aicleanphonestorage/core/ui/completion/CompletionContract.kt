package com.example.aicleanphonestorage.core.ui.completion

import android.content.Context
import android.content.Intent

/** Activity Result 只返回用户选择，删除原图必须交回原业务协调器再次确认。 */
internal object CompletionContract {
    const val ACTION = "completion.action"
    const val OPERATION = "completion.operation"
    const val CONTINUE = "continue"
    const val REMOVE_ORIGINALS = "remove_originals"

    fun intent(context: Context, report: CompletionReport) =
        Intent(context, CompletionActivity::class.java).apply {
            putExtra("completion.kind", report.kind.name)
            putExtra("completion.count", report.completed)
            putExtra("completion.failed", report.failed)
            putExtra("completion.skipped", report.skipped)
            putExtra("completion.freed", report.freedBytes)
            putExtra("completion.reduced", report.reducedBytes)
            putExtra("completion.remaining", report.originalsRemaining)
            putExtra("completion.removable", report.removableOriginals)
            putExtra(OPERATION, report.operationId)
        }

    fun read(intent: Intent): CompletionReport? {
        val kind =
            CompletionKind.entries.firstOrNull {
                it.name == intent.getStringExtra("completion.kind")
            } ?: return null
        return CompletionReport(
            kind,
            intent.getIntExtra("completion.count", 0).coerceAtLeast(0),
            intent.getIntExtra("completion.failed", 0).coerceAtLeast(0),
            intent.getIntExtra("completion.skipped", 0).coerceAtLeast(0),
            intent.getLongExtra("completion.freed", 0).coerceAtLeast(0),
            intent.getLongExtra("completion.reduced", 0).coerceAtLeast(0),
            intent.getIntExtra("completion.remaining", 0).coerceAtLeast(0),
            intent.getIntExtra("completion.removable", 0).coerceAtLeast(0),
            intent.getLongExtra(OPERATION, 0),
        )
    }
}
