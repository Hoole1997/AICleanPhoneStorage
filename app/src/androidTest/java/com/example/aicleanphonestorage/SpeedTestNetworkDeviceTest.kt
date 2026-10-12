package com.example.aicleanphonestorage

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import java.util.Locale
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机网络上的一次真实 Cloudflare 测速（无界面）：验证端点可用、阶段时限与结果字段。
 * 会消耗至多约 70MB 流量；数值写入 instrumentation 输出，仅作本次测量记录，不作为性能基准。
 */
@RunWith(AndroidJUnit4::class)
class SpeedTestNetworkDeviceTest {
    @Test fun realSpeedTestCompletesWithinBounds() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CleanApplication
        val started = System.nanoTime()
        val events = withTimeout(60_000) { app.container.speedTestRepository.run().toList() }
        val seconds = (System.nanoTime() - started) / 1e9
        val result = (events.last() as SpeedTestEvent.Completed).result
        val network = app.container.speedTestNetwork.current()
        val summary = String.format(Locale.US,
            "network=%s carrier=%s model=%s node=%s ray=%s download=%.0fKB/s upload=%.0fKB/s ping=%.1fms dJitter=%s uJitter=%s duration=%.1fs progressEvents=%d",
            network.type, network.carrier, network.model, result.server.node, result.testId,
            result.metrics.downloadBytesPerSecond!! / 1000, result.metrics.uploadBytesPerSecond!! / 1000, result.metrics.pingMs,
            result.metrics.downloadJitterMs, result.metrics.uploadJitterMs, seconds, events.size - 1)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("speedtest", summary) })
        android.util.Log.i("SpeedTestDevice", summary)
        assertNotNull(result.server.node)
        assertNotNull(result.metrics.pingMs)
        // 两个传输阶段各 ≤10 秒，加上连接与延迟测量，整体应明显短于 40 秒。
        assertTrue("took $seconds s", seconds < 40)
    }
}
