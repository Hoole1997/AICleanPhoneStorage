package com.example.aicleanphonestorage.core.ui.loading

/** 展示时间轴统一接口；已知总量的应用查询与总量逐步发现的文件扫描使用各自的进度策略。 */
internal interface EntryProgressTimeline {
    fun report(progress: TaskProgress)

    fun complete(appCount: Int, stage: String = "APPLICATIONS")

    fun remainingMillis(): Long

    fun frame(): TimedEntryProgress.Frame
}
