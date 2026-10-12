package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.CloudflareSpeedTest
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestLimits
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 用本地 MockWebServer 模拟 Cloudflare 端点，验证预算、时限、失败映射与真实请求形态。 */
class CloudflareSpeedTestTest {
    private val server = MockWebServer()
    private val downloaded = AtomicLong()
    private val uploaded = AtomicLong()
    private var downloadDelayMs = 0L
    private var failDownloads = false
    private val limits = SpeedTestLimits(
        downloadBytes = 400_000,
        uploadBytes = 200_000,
        phaseNanos = TimeUnit.SECONDS.toNanos(3),
        downloadChunkBytes = 64_000,
        minUploadChunkBytes = 16_000,
        maxUploadChunkBytes = 64_000,
        latencySamples = 3,
        probeIntervalMillis = 30,
        sampleIntervalMillis = 20,
    )

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // 模拟 Cloudflare：所有请求都必须禁用压缩，保证计数的是线路字节。
                check(request.getHeader("Accept-Encoding") == "identity")
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/cdn-cgi/trace") ->
                        MockResponse().setHeader("cf-ray", "8f2c1a-HKG").setBody("fl=1\ncolo=HKG\nloc=HK\n")
                    path.startsWith("/__down") -> {
                        if (failDownloads) return MockResponse().setResponseCode(500)
                        val bytes = request.requestUrl!!.queryParameter("bytes")!!.toLong()
                        downloaded.addAndGet(bytes)
                        MockResponse().setBody(Buffer().write(ByteArray(bytes.toInt())))
                            .apply { if (downloadDelayMs > 0) throttleBody(16_000, downloadDelayMs, TimeUnit.MILLISECONDS) }
                    }
                    path.startsWith("/__up") -> {
                        uploaded.addAndGet(request.bodySize)
                        MockResponse()
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @After fun tearDown() = server.shutdown()

    private fun repository(online: Boolean = true, limits: SpeedTestLimits = this.limits) =
        CloudflareSpeedTest({ online }, limits, server.url("/").toString().trimEnd('/'))

    @Test fun `completes with node ray id and every metric within byte budgets`() = runBlocking {
        val events = withTimeout(20_000) { repository().run().toList() }
        val result = (events.last() as SpeedTestEvent.Completed).result
        assertEquals("HKG", result.server.node)
        assertEquals("Cloudflare", result.server.provider)
        assertEquals("8f2c1a", result.testId)
        with(result.metrics) {
            assertTrue(downloadBytesPerSecond!! > 0)
            assertTrue(uploadBytesPerSecond!! > 0)
            assertNotNull(pingMs)
        }
        assertTrue("download budget", downloaded.get() <= limits.downloadBytes)
        assertTrue("upload budget", uploaded.get() <= limits.uploadBytes)
        val progress = events.filterIsInstance<SpeedTestEvent.Progress>().map { it.progress }
        assertEquals(listOf(SpeedTestPhase.Connecting, SpeedTestPhase.Latency, SpeedTestPhase.Download, SpeedTestPhase.Upload),
            progress.map { it.phase }.distinct())
        assertTrue(progress.all { it.fraction in 0f..1f })
    }

    @Test fun `slow network stops at the phase deadline instead of downloading the whole budget`() = runBlocking {
        downloadDelayMs = 200 // 每 200ms 只放行 16KB ≈ 80KB/s
        val short = limits.copy(phaseNanos = TimeUnit.MILLISECONDS.toNanos(800), downloadBytes = 4_000_000)
        val started = System.nanoTime()
        val result = withTimeout(20_000) { repository(limits = short).run().toList() }.last() as SpeedTestEvent.Completed
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("finished in ${seconds}s", seconds < 6)
        // 4 路各约 80KB/s：结果应接近限速而远低于本机不限速吞吐（数百 MB/s），且只请求了预算的一小部分。
        assertTrue(result.result.metrics.downloadBytesPerSecond!! < 1_000_000)
        assertTrue("requested ${downloaded.get()} bytes", downloaded.get() < 1_000_000)
    }

    @Test fun `offline device fails fast without any request`() = runBlocking {
        try {
            repository(online = false).run().toList()
            fail("expected failure")
        } catch (error: SpeedTestException) {
            assertEquals(SpeedTestException.Reason.NoInternet, error.reason)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `server errors map to a generic failure`() = runBlocking {
        failDownloads = true
        try {
            withTimeout(20_000) { repository().run().toList() }
            fail("expected failure")
        } catch (error: SpeedTestException) {
            assertEquals(SpeedTestException.Reason.Failed, error.reason)
        }
    }
}
