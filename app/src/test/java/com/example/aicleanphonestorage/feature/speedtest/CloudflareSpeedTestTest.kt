package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.CloudflareSpeedTest
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestConnectivity
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestConnectivityState
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestLimits
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var downloadStatus = 200
    private var bytesPerPeriod = 16_000L
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
                        if (downloadStatus != 200) return MockResponse().setResponseCode(downloadStatus)
                        val bytes = request.requestUrl!!.queryParameter("bytes")!!.toLong()
                        downloaded.addAndGet(bytes)
                        MockResponse().setBody(Buffer().write(ByteArray(bytes.toInt())))
                            .apply { if (downloadDelayMs > 0) throttleBody(bytesPerPeriod, downloadDelayMs, TimeUnit.MILLISECONDS) }
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

    /** 可控的系统网络状态；[switches] 发射即模拟默认网络切换。 */
    private class FakeConnectivity(var state: SpeedTestConnectivityState = SpeedTestConnectivityState.Online(1)) : SpeedTestConnectivity {
        val switches = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override fun connectivity() = state
        override fun switchedFrom(networkId: Long): Flow<Unit> = switches
    }

    private val connectivity = FakeConnectivity()

    private fun repository(limits: SpeedTestLimits = this.limits) =
        CloudflareSpeedTest(connectivity, limits, server.url("/").toString().trimEnd('/'))

    private suspend fun failure(limits: SpeedTestLimits = this.limits): SpeedTestException.Reason = try {
        withTimeout(20_000) { repository(limits).run().toList() }
        fail("expected failure")
        error("unreachable")
    } catch (error: SpeedTestException) {
        error.reason
    }

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

    @Test fun `device network state fails fast without any request`() = runBlocking {
        connectivity.state = SpeedTestConnectivityState.Offline
        assertEquals(SpeedTestException.Reason.NoInternet, failure())
        connectivity.state = SpeedTestConnectivityState.CaptivePortal
        assertEquals(SpeedTestException.Reason.Restricted, failure())
        connectivity.state = SpeedTestConnectivityState.Unavailable
        assertEquals(SpeedTestException.Reason.NetworkInfoUnavailable, failure())
        assertEquals(0, server.requestCount)
    }

    @Test fun `server errors and blocked access map to their own messages`() = runBlocking {
        downloadStatus = 500
        assertEquals(SpeedTestException.Reason.ServerError, failure())
        downloadStatus = 403
        assertEquals(SpeedTestException.Reason.Restricted, failure())
    }

    @Test fun `default network switch aborts the test`() = runBlocking {
        downloadDelayMs = 200
        val reason = coroutineScope {
            launch { delay(300); connectivity.switches.emit(Unit) }
            failure(limits.copy(downloadBytes = 4_000_000))
        }
        assertEquals(SpeedTestException.Reason.NetworkChanged, reason)
    }

    @Test fun `too little data within the phase is a weak network`() = runBlocking {
        downloadDelayMs = 400
        bytesPerPeriod = 1_000 // 4 路各约 2.5KB/s，800ms 内远低于最低 64KB
        assertEquals(SpeedTestException.Reason.WeakNetwork,
            failure(limits.copy(phaseNanos = TimeUnit.MILLISECONDS.toNanos(800), downloadBytes = 4_000_000, minTransferBytes = 64_000)))
    }

    @Test fun `exceeding the total limit is a timeout`() = runBlocking {
        downloadDelayMs = 200
        assertEquals(SpeedTestException.Reason.Timeout, failure(limits.copy(downloadBytes = 4_000_000, totalMillis = 600)))
    }
}
