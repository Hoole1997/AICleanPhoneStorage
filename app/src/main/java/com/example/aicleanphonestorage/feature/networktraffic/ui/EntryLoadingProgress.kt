package com.example.aicleanphonestorage.feature.networktraffic.ui

import com.example.aicleanphonestorage.core.ui.loading.TaskProgress
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryProgress
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficProgress
import com.example.aicleanphonestorage.feature.networktraffic.data.TrafficStage

/** 流量阶段到通用入口时间轴的适配；通知模块使用同一时间轴实现。 */
internal class EntryLoadingProgress(startedAt: Long, durationMillis: Long, clock: () -> Long) {
    data class Frame(val detail: TrafficProgress, val percent: Int?)
    private val delegate = TimedEntryProgress(startedAt, durationMillis, clock, "MOBILE")
    fun report(progress: TrafficProgress) = delegate.report(TaskProgress(progress.stage.name, progress.completed, progress.total))
    fun complete(count: Int) = delegate.complete(count)
    fun remainingMillis() = delegate.remainingMillis()
    fun frame(): Frame = delegate.frame().let { Frame(TrafficProgress(TrafficStage.valueOf(it.detail.stage), it.detail.completed, it.detail.total), it.percent) }
}
