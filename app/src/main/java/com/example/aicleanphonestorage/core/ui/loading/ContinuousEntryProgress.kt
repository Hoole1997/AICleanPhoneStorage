package com.example.aicleanphonestorage.core.ui.loading

import java.util.concurrent.atomic.AtomicReference

/**
 * 文件扫描的连续展示进度：总数尚未知时根据已发现工作量估计阶段进度，不能视为精确文件百分比。 所有阶段共用 0–90%，结果准备完成才解锁最后 10%；广告最短展示时间仍可延后进度和计数。
 * 渲染只在主线程推进，后台扫描仅写入不可变事实，保证阶段/总量变化不会让百分比倒退。
 */
internal class ContinuousEntryProgress(
    private val startedAt: Long,
    private val durationMillis: Long,
    private val clock: () -> Long,
    stages: List<String>,
) : EntryProgressTimeline {
    private val phases = stages.distinct().ifEmpty { listOf("FILES") }

    private data class Actual(val detail: TaskProgress, val finished: Boolean = false)

    private val actual = AtomicReference(Actual(TaskProgress(phases.first(), 0)))
    private var finishedAt: Long? = null
    private var completionStart = 0
    private var displayed = 0
    private val rampMillis = (durationMillis - 100).coerceAtLeast(1)

    override fun report(progress: TaskProgress) {
        actual.updateAndGet { if (it.finished) it else Actual(progress) }
    }

    override fun complete(appCount: Int, stage: String) {
        completionStart = displayed
        actual.set(Actual(TaskProgress(stage, appCount, appCount, actual.get().detail.bytes), true))
        finishedAt = clock()
    }

    override fun remainingMillis(): Long {
        val now = clock()
        val adRemaining = startedAt + durationMillis - now
        val completionRemaining = finishedAt?.let { it + FINISH_RAMP + FINISH_HOLD - now } ?: 0
        return maxOf(0, adRemaining, completionRemaining)
    }

    override fun frame(): TimedEntryProgress.Frame {
        val now = clock()
        val fact = actual.get().detail
        val timeFraction = ((now - startedAt).toDouble() / rampMillis).coerceIn(0.0, 1.0)
        val finished = finishedAt
        val target =
            if (finished != null) {
                val fraction = ((now - finished).toDouble() / FINISH_RAMP).coerceIn(0.0, 1.0)
                completionStart + (100 - completionStart) * fraction
            } else {
                val phase = phases.indexOf(fact.stage).coerceAtLeast(0)
                val completed = (fact.completed ?: 0).coerceAtLeast(0).toDouble()
                val total = fact.total
                // 未知总量采用有上限的工作量估计，不重扫文件系统只为预先统计总数。
                val fraction =
                    if (total != null && total > 0) (completed / total).coerceIn(0.0, 1.0)
                    else completed / (completed + 200.0)
                (phase + fraction) / phases.size * 90.0
            }
        displayed = maxOf(displayed, minOf(target, timeFraction * 100).toInt().coerceIn(0, 100))
        val completed = fact.completed
        val count =
            if (completed == null) null
            else {
                val total = (fact.total ?: completed).coerceAtLeast(0)
                minOf(completed, (total.toLong() * displayed / 100).toInt())
            }
        // 容量与百分比共用同一时间轴，仅延后呈现已确认的候选；100% 精确收敛到结果摘要。
        val bytes = fact.bytes?.let { total ->
            // 先除后乘避免 Long 乘百分比溢出。
            total / 100 * displayed + total % 100 * displayed / 100
        }
        return TimedEntryProgress.Frame(fact.copy(completed = count, bytes = bytes), displayed)
    }

    companion object {
        private const val FINISH_RAMP = 300L
        private const val FINISH_HOLD = 100L
    }
}
