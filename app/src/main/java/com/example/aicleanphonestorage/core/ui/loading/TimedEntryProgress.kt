package com.example.aicleanphonestorage.core.ui.loading

import java.util.concurrent.atomic.AtomicReference

internal data class TaskProgress(val stage: String, val completed: Int? = null, val total: Int? = null) {
    val percent: Int? get() = if (completed != null && total != null && total > 0) (completed.toLong() * 100 / total).toInt().coerceIn(0, 100) else null
}


/**
 * 将真实查询进度与入口展示窗口合并，计数和百分比只从同一帧产生。
 * 可以延后展示已完成的数量，但绝不提前声称未完成的数据已经完成。
 * 无 Android 依赖，使用注入的单调时钟，可精确测试快查询、慢查询和零应用。
 */
internal class TimedEntryProgress(
    private val startedAt: Long,
    private val durationMillis: Long,
    private val monotonicMillis: () -> Long,
    initialStage: String = "APPLICATIONS",
) : EntryProgressTimeline {
    data class Frame(val detail: TaskProgress, val percent: Int?)
    private data class Actual(val detail: TaskProgress, val finished: Boolean = false)
    private val actual = AtomicReference(Actual(TaskProgress(initialStage)))
    // 最后的100ms用于呈现完成帧，包含在随机总时长内，不再额外增加等待。
    private val completionFrameMillis = minOf(100L, durationMillis / 4)
    private val rampMillis = (durationMillis - completionFrameMillis).coerceAtLeast(1)

    override fun report(progress: TaskProgress) {
        actual.updateAndGet { if (it.finished) it else Actual(progress) }
    }
    override fun complete(appCount: Int, stage: String) {
        actual.set(Actual(TaskProgress(stage, appCount, appCount), finished = true))
    }
    override fun remainingMillis(): Long = (durationMillis - elapsed()).coerceAtLeast(0)
    private fun elapsed(): Long = (monotonicMillis() - startedAt).coerceAtLeast(0)

    override fun frame(): Frame {
        val source = actual.get()
        val detail = source.detail
        val fraction = (elapsed().toDouble() / rampMillis).coerceIn(0.0, 1.0)
        val total = detail.total
        val completed = detail.completed
        if (total == null || completed == null || total <= 0) {
            // 没有应用时展示“准备结果”的整体进度，不显示无意义的0/0或编造应用数量。
            return Frame(detail, if (source.finished) (fraction * 100).toInt() else null)
        }
        val actualLimit = completed.coerceIn(0, total)
        val completionLimit = if (source.finished) total else (total - 1).coerceAtLeast(0)
        val displayed = minOf(actualLimit, completionLimit, (total * fraction).toInt())
        val presented = detail.copy(completed = displayed)
        return Frame(presented, presented.percent)
    }
}
