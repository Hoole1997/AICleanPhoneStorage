package com.example.aicleanphonestorage

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.CloudflareSpeedTestEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.HttpSpeedTest
import com.example.aicleanphonestorage.feature.speedtest.data.LibreSpeedEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestLimits
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestRepository
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestResult
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 真机网络上的真实测速（无界面）：验证实际选中节点、阶段时限与结果字段。
 * 会消耗至多约 70MB 流量；数值写入 instrumentation 输出，仅作本次测量记录，不作为性能基准。
 */
@RunWith(AndroidJUnit4::class)
class SpeedTestNetworkDeviceTest {
    @Test fun realSpeedTestCompletesWithinBounds() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CleanApplication
        measure(app.container.speedTestRepository)
    }

    /** 首路 503 仅由本测试拦截器注入；备用上传/下载仍是真实网络，不能当作 CF 真实故障记录。 */
    @Test fun realBackupCompletesAfterInjectedPrimaryFailure() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CleanApplication
        val client = HttpSpeedTest.defaultClient().newBuilder().addInterceptor { chain ->
            if (chain.request().url.host == "speed.cloudflare.com") {
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("Injected test failure").body(ByteArray(0).toResponseBody()).build()
            } else chain.proceed(chain.request())
        }.build()
        val result = measure(HttpSpeedTest(app.container.speedTestNetwork,
            SpeedTestLimits(downloadBytes = 8_388_608, uploadBytes = 2_000_000,
                phaseNanos = TimeUnit.SECONDS.toNanos(5), latencySamples = 3),
            // 只影响本仪器测试，允许在公共节点变更时先验证候选，不改应用生产路线。
            listOf(CloudflareSpeedTestEndpoint(), LibreSpeedEndpoint(
                InstrumentationRegistry.getArguments().getString("backupBaseUrl") ?: "https://la.speedtest.clouvider.net/backend",
                InstrumentationRegistry.getArguments().getString("backupNode") ?: "Los Angeles")),
            clientFactory = { client }))
        assertTrue(result.server.provider == "LibreSpeed")
        assertTrue(result.testId == null)
    }

    private suspend fun measure(repository: SpeedTestRepository): SpeedTestResult {
        val started = System.nanoTime()
        val events = withTimeout(65_000) { repository.run().toList() }
        val seconds = (System.nanoTime() - started) / 1e9
        val result = (events.last() as SpeedTestEvent.Completed).result
        val summary = String.format(Locale.US,
            "provider=%s node=%s download=%.0fKB/s upload=%.0fKB/s ping=%.1fms dJitter=%s uJitter=%s duration=%.1fs progressEvents=%d",
            result.server.provider, result.server.node,
            result.metrics.downloadBytesPerSecond!! / 1000, result.metrics.uploadBytesPerSecond!! / 1000, result.metrics.pingMs,
            result.metrics.downloadJitterMs, result.metrics.uploadJitterMs, seconds, events.size - 1)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("speedtest", summary) })
        android.util.Log.i("SpeedTestDevice", summary)
        assertTrue(result.server.provider.isNotBlank())
        assertNotNull(result.metrics.pingMs)
        // 两路共用 60 秒期限，外层只给设备调度和测试收尾留少量余量。
        assertTrue("took $seconds s", seconds < 62)
        return result
    }
}
