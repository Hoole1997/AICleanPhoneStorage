package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMath
import com.example.aicleanphonestorage.feature.speedtest.data.TransferSample
import com.example.aicleanphonestorage.feature.speedtest.data.reserve
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestMathTest {
    private val second = 1_000_000_000L

    @Test fun `median and jitter follow documented definitions`() {
        assertNull(SpeedTestMath.median(emptyList()))
        assertEquals(20.0, SpeedTestMath.median(listOf(30.0, 10.0, 20.0))!!, 0.0)
        assertEquals(15.0, SpeedTestMath.median(listOf(10.0, 20.0))!!, 0.0)
        assertNull(SpeedTestMath.jitter(listOf(10.0)))
        // |20-10| + |15-20| + |25-15| = 25，三个差值平均。
        assertEquals(25.0 / 3, SpeedTestMath.jitter(listOf(10.0, 20.0, 15.0, 25.0))!!, 1e-9)
    }

    @Test fun `live rate uses only the recent window`() {
        val samples = listOf(TransferSample(0, 0), TransferSample(second, 1_000), TransferSample(2 * second, 5_000))
        assertEquals(4_000.0, SpeedTestMath.liveRate(samples, second)!!, 1e-6)
        assertNull(SpeedTestMath.liveRate(listOf(TransferSample(0, 0)), second))
    }

    @Test fun `sustained rate skips slow start warm up`() {
        // 前 1 秒慢启动只有 100B，之后 9 秒每秒 1000B：去掉 25%（上限 1.5 秒）预热后约 1000B/s。
        val samples = buildList {
            add(TransferSample(0, 0))
            add(TransferSample(second, 100))
            for (i in 2..10) add(TransferSample(i * second, 100L + (i - 1) * 1000))
        }
        assertEquals(1000.0, SpeedTestMath.sustainedRate(samples, 0.25, 1_500_000_000)!!, 1e-6)
        // 达到流量上限提前结束的极短阶段仍给出结果。
        assertEquals(2_000_000.0, SpeedTestMath.sustainedRate(listOf(TransferSample(0, 0), TransferSample(second / 2, 1_000_000)), 0.25, 1_500_000_000)!!, 1e-6)
        assertNull(SpeedTestMath.sustainedRate(listOf(TransferSample(0, 0)), 0.25, 1_500_000_000))
    }

    @Test fun `budget reservation never exceeds the cap`() {
        val budget = AtomicLong(25)
        assertEquals(10, budget.reserve(10))
        assertEquals(10, budget.reserve(10))
        assertEquals(5, budget.reserve(10))
        assertEquals(0, budget.reserve(10))
    }
}
