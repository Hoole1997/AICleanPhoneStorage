package com.example.aicleanphonestorage.core.ui.loading

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

internal class EntryLoadingStalledException(val progress: TaskProgress) :
    IOException("Entry scan stopped reporting progress: stage=${progress.stage}, completed=${progress.completed}, total=${progress.total}")

/** 展示时间与真实任务分离；以实际进度报告检测停滞，不用动画帧或虚构百分比延长等待。 */
internal class TimedEntryLoader(
    private val duration: () -> Long = ::defaultDurationMillis,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val stallTimeoutMillis: Long = 60_000L,
) {
    private data class Report(val progress: TaskProgress, val at: Long)

    suspend fun <T> load(
        initialStage: String = "APPLICATIONS",
        finalStage: String = "APPLICATIONS",
        continuousStages: List<String>? = null,
        count: (T) -> Int,
        onFrame: (TimedEntryProgress.Frame) -> Unit,
        onStalled: () -> Unit = {},
        work: suspend ((TaskProgress) -> Unit) -> T,
    ): T = coroutineScope {
        val millis = duration().coerceIn(0, 10_000)
        val instant = millis == 0L && continuousStages == null
        val startedAt = clock()
        val timeline: EntryProgressTimeline = if (continuousStages == null)
            TimedEntryProgress(startedAt, millis, clock, initialStage)
        else ContinuousEntryProgress(startedAt, millis, clock, continuousStages)
        val latest = AtomicReference(Report(TaskProgress(initialStage), startedAt))
        var workFinished = false
        if (!instant) onFrame(timeline.frame())
        val ticker = launch {
            while (isActive) {
                val report = latest.get()
                if (!workFinished && clock() - report.at >= stallTimeoutMillis.coerceAtLeast(1)) {
                    // 先结束界面等待，再取消任务。阻塞的系统 I/O 可能不能立即响应取消。
                    onStalled()
                    currentCoroutineContext().ensureActive()
                    throw EntryLoadingStalledException(report.progress)
                }
                if (!instant) onFrame(timeline.frame())
                delay(50)
            }
        }
        try {
            val result = work { progress ->
                latest.updateAndGet { previous ->
                    if (previous.progress == progress) previous else Report(progress, clock())
                }
                timeline.report(progress)
                if (instant) onFrame(TimedEntryProgress.Frame(progress, progress.percent))
            }
            currentCoroutineContext().ensureActive()
            workFinished = true
            if (instant) return@coroutineScope result
            timeline.complete(count(result), finalStage)
            onFrame(timeline.frame())
            val remaining = timeline.remainingMillis()
            if (remaining > 0) delay(remaining)
            result
        } catch (error: CancellationException) {
            // 内部超时/自取消不应使 ViewModel 永久停在 Loading；外部取消仍按结构化取消传播。
            currentCoroutineContext().ensureActive()
            throw IOException("Entry scan interrupted before completion", error)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (error is IOException || error is SecurityException) throw error
            throw IOException("Entry scan failed before completion", error)
        } finally {
            ticker.cancel()
        }
    }

    companion object {
        fun defaultDurationMillis(): Long = Random.nextLong(2000, 4001)
    }
}
