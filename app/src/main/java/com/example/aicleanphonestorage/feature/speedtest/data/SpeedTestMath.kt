package com.example.aicleanphonestorage.feature.speedtest.data

import kotlin.math.abs

/** 传输阶段的累计字节采样（相对阶段开始的纳秒数）。 */
internal data class TransferSample(val elapsedNanos: Long, val bytes: Long)

/** 测速统计口径集中在纯函数中，便于单元测试，不依赖网络或 Android。 */
internal object SpeedTestMath {
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    /** 中位数抗个别抖动；样本为空返回 null。 */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** 抖动 = 相邻延迟样本差值绝对值的平均数；少于两个样本无法计算。 */
    fun jitter(values: List<Double>): Double? {
        if (values.size < 2) return null
        return values.zipWithNext { a, b -> abs(b - a) }.average()
    }

    /** 最近 [windowNanos] 内的实时速度，用于测速中的数字刷新。 */
    fun liveRate(samples: List<TransferSample>, windowNanos: Long): Double? {
        val last = samples.lastOrNull() ?: return null
        val first = samples.firstOrNull { last.elapsedNanos - it.elapsedNanos <= windowNanos } ?: return null
        val nanos = last.elapsedNanos - first.elapsedNanos
        if (nanos <= 0) return null
        return (last.bytes - first.bytes) * NANOS_PER_SECOND / nanos
    }

    /**
     * 阶段结果：跳过 TCP 慢启动的预热段（阶段时长的 [warmupFraction]，最长 [maxWarmupNanos]）后，
     * 按剩余区间的字节数/时长计算平均吞吐。快速网络提前达到流量上限时仍按实际耗时计算。
     */
    fun sustainedRate(samples: List<TransferSample>, warmupFraction: Double, maxWarmupNanos: Long): Double? {
        val last = samples.lastOrNull() ?: return null
        if (last.elapsedNanos <= 0 || last.bytes <= 0) return null
        val warmup = minOf((last.elapsedNanos * warmupFraction).toLong(), maxWarmupNanos)
        val start = samples.lastOrNull { it.elapsedNanos <= warmup } ?: TransferSample(0, 0)
        val nanos = last.elapsedNanos - start.elapsedNanos
        val bytes = last.bytes - start.bytes
        if (nanos <= 0 || bytes <= 0) return last.bytes * NANOS_PER_SECOND / last.elapsedNanos
        return bytes * NANOS_PER_SECOND / nanos
    }
}
