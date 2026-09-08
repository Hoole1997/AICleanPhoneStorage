package com.example.aicleanphonestorage.core.ui.loading

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 所有首页入口共用的2–4秒展示、计数/百分比时间轴及结构化取消逻辑。 */
internal class TimedEntryLoader(
    private val duration: () -> Long = ::defaultDurationMillis,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun <T> load(
        initialStage: String = "APPLICATIONS",
        finalStage: String = "APPLICATIONS",
        count: (T) -> Int,
        onFrame: (TimedEntryProgress.Frame) -> Unit,
        work: suspend ((TaskProgress) -> Unit) -> T,
    ): T = coroutineScope {
        val millis = duration().coerceIn(0, 10_000)
        if (millis == 0L) return@coroutineScope work { onFrame(TimedEntryProgress.Frame(it, it.percent)) }
        val timeline = TimedEntryProgress(clock(), millis, clock, initialStage)
        val ticker = launch {
            while (isActive) { onFrame(timeline.frame()); delay(50) }
        }
        try {
            val result = work(timeline::report)
            currentCoroutineContext().ensureActive()
            timeline.complete(count(result), finalStage)
            onFrame(timeline.frame())
            val remaining = timeline.remainingMillis()
            if (remaining > 0) delay(remaining)
            result
        } finally { ticker.cancel() }
    }
    companion object {
        fun defaultDurationMillis(): Long = Random.nextLong(2000, 4001)
    }
}
