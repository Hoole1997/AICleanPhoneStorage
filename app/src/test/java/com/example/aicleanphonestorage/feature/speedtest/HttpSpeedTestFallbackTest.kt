package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.CloudflareSpeedTestEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.HttpSpeedTest
import com.example.aicleanphonestorage.feature.speedtest.data.LibreSpeedEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestConnectivity
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestConnectivityState
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestLimits
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestMetrics
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestPhase
import java.io.Closeable
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 两个隔离的真实 HTTP 服务模拟路线故障，验证兜底只在同一可用网络上执行且不增加整轮资源上限。 */
class HttpSpeedTestFallbackTest {
    private val limits = SpeedTestLimits(
        downloadBytes = 320_000,
        uploadBytes = 160_000,
        phaseNanos = TimeUnit.SECONDS.toNanos(2),
        downloadStreams = 2,
        uploadStreams = 1,
        downloadChunkBytes = 64_000,
        minUploadChunkBytes = 16_000,
        maxUploadChunkBytes = 64_000,
        latencySamples = 2,
        latencyBudgetNanos = TimeUnit.MILLISECONDS.toNanos(300),
        probeIntervalMillis = 1_000,
        sampleIntervalMillis = 20,
        totalMillis = 5_000,
        primaryMillis = 2_500,
    )

    @Test fun `primary success never contacts the backup`() = runBlocking {
        withNodes { primary, backup ->
            val events = repository(primary, backup).events()
            assertEquals("AAA", events.result().server.node)
            assertEquals(1, events.filterIsInstance<SpeedTestEvent.Completed>().size)
            assertEquals(0, backup.server.requestCount)
            assertEquals(1, events.progress().count { it.phase == SpeedTestPhase.Connecting })
        }
    }

    @Test fun `connection download and upload failures each restart on the backup with empty metrics`() = runBlocking {
        Stage.entries.forEach { failedStage ->
            withNodes { primary, backup ->
                primary.failedStage = failedStage
                val events = repository(primary, backup).events()
                assertEquals("backup after $failedStage", "BBB", events.result().server.node)
                assertEquals(1, events.filterIsInstance<SpeedTestEvent.Completed>().size)
                assertTrue(backup.downloadBytes.get() > 0)
                assertTrue(backup.uploadBytes.get() > 0)
                val progress = events.progress()
                val connectionIndexes = progress.indices.filter { progress[it].phase == SpeedTestPhase.Connecting }
                assertEquals("exactly one fallback after $failedStage", 2, connectionIndexes.size)
                val restarted = progress[connectionIndexes.last()]
                assertEquals(0f, restarted.fraction)
                assertEquals(SpeedTestMetrics(), restarted.metrics)
                assertNull(restarted.server)
                assertTrue(progress.drop(connectionIndexes.last() + 1).all { it.server?.node == "BBB" })
                if (failedStage == Stage.Upload) {
                    // 主路下载已经完成，备用仍必须重测下载，最终指标不能拼接主路下载与备用上传。
                    assertEquals(limits.downloadBytes * 4 / 5, primary.downloadBytes.get())
                    assertTrue(progress.take(connectionIndexes.last()).any {
                        it.server?.node == "AAA" && it.metrics.downloadBytesPerSecond != null
                    })
                }
            }
        }
    }

    @Test fun `both routes failing surfaces the last route reason without completion`() = runBlocking {
        withNodes { primary, backup ->
            primary.failedStage = Stage.Connection
            primary.failureCode = 500
            backup.failedStage = Stage.Connection
            backup.failureCode = 403
            val events = ArrayList<SpeedTestEvent>()
            assertEquals(SpeedTestException.Reason.Restricted,
                repository(primary, backup).failure(events))
            assertEquals(2, events.progress().count { it.phase == SpeedTestPhase.Connecting })
            assertTrue(events.none { it is SpeedTestEvent.Completed })
        }
    }

    @Test fun `primary DNS error still uses backup when the device remains online`() = runBlocking {
        withNodes { primary, backup ->
            val clientFactory = {
                HttpSpeedTest.defaultClient().newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname == "primary.invalid") throw UnknownHostException("simulated route DNS failure")
                        return Dns.SYSTEM.lookup(hostname)
                    }
                }).build()
            }
            val repository = HttpSpeedTest(FakeConnectivity(), limits,
                listOf(CloudflareSpeedTestEndpoint("https://primary.invalid"), backup.endpoint),
                clientFactory = clientFactory)
            assertEquals("BBB", repository.events().result().server.node)
            assertEquals(0, primary.server.requestCount)
            assertTrue(backup.server.requestCount > 0)
        }
    }

    @Test fun `blocked native DNS does not delay fallback until the DNS thread returns`() = runBlocking {
        withNodes { primary, backup ->
            val enteredDns = CountDownLatch(1)
            val releaseDns = CountDownLatch(1)
            val dnsReturned = AtomicBoolean(false)
            val clientFactory = {
                HttpSpeedTest.defaultClient().newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname != "primary.invalid") return Dns.SYSTEM.lookup(hostname)
                        enteredDns.countDown()
                        try {
                            // 模拟系统 DNS 不响应 Call.cancel；finally 会释放，失败时也不会留下常驻测试线程。
                            releaseDns.await(2, TimeUnit.SECONDS)
                            throw UnknownHostException("simulated blocking native DNS")
                        } finally {
                            dnsReturned.set(true)
                        }
                    }
                }).build()
            }
            val repository = HttpSpeedTest(FakeConnectivity(), limits.copy(totalMillis = 1_000, primaryMillis = 150),
                listOf(CloudflareSpeedTestEndpoint("https://primary.invalid"), backup.endpoint),
                clientFactory = clientFactory)
            try {
                val started = System.nanoTime()
                assertEquals("BBB", repository.events().result().server.node)
                val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                assertEquals("primary DNS was entered", 0L, enteredDns.count)
                assertFalse("backup must finish before blocked DNS returns", dnsReturned.get())
                assertTrue("fallback took ${elapsedMillis}ms", elapsedMillis < 900)
                assertEquals(0, primary.server.requestCount)
                assertTrue(backup.server.requestCount > 0)
            } finally {
                releaseDns.countDown()
            }
        }
    }

    @Test fun `offline captive portal and unavailable network fail before contacting either route`() = runBlocking {
        withNodes { primary, backup ->
            listOf(
                SpeedTestConnectivityState.Offline to SpeedTestException.Reason.NoInternet,
                SpeedTestConnectivityState.CaptivePortal to SpeedTestException.Reason.Restricted,
                SpeedTestConnectivityState.Unavailable to SpeedTestException.Reason.NetworkInfoUnavailable,
            ).forEach { (state, reason) ->
                assertEquals(reason, repository(primary, backup, FakeConnectivity(state)).failure())
            }
            assertEquals(0, primary.server.requestCount)
            assertEquals(0, backup.server.requestCount)
        }
    }

    @Test fun `device becoming offline during primary failure does not start backup`() = runBlocking {
        withNodes { primary, backup ->
            val connectivity = FakeConnectivity()
            primary.failedStage = Stage.Connection
            primary.onRequest = { connectivity.state = SpeedTestConnectivityState.Offline }
            assertEquals(SpeedTestException.Reason.NoInternet,
                repository(primary, backup, connectivity).failure())
            assertEquals(0, backup.server.requestCount)
        }
    }

    @Test fun `switching the default network aborts without trying the backup`() = runBlocking {
        withNodes { primary, backup ->
            val connectivity = FakeConnectivity()
            primary.failedStage = Stage.Connection
            primary.onRequest = {
                connectivity.state = SpeedTestConnectivityState.Online(2)
                connectivity.switches.tryEmit(Unit)
            }
            assertEquals(SpeedTestException.Reason.NetworkChanged,
                repository(primary, backup, connectivity).failure())
            assertEquals(0, backup.server.requestCount)
        }
    }

    @Test fun `user cancellation closes the active call without starting the backup`() = runBlocking {
        withNodes { primary, backup ->
            primary.delayedStage = Stage.Connection
            primary.delayMillis = 800
            val received = CompletableDeferred<Unit>()
            primary.onRequest = { received.complete(Unit) }
            val events = ArrayList<SpeedTestEvent>()
            coroutineScope {
                val collecting = launch { repository(primary, backup).run().toList(events) }
                withTimeout(2_000) { received.await() }
                withTimeout(1_000) { collecting.cancelAndJoin() }
                assertTrue(collecting.isCancelled)
            }
            assertEquals(0, backup.server.requestCount)
            assertTrue(events.none { it is SpeedTestEvent.Completed })
        }
    }

    @Test fun `primary deadline cancels that route and allows the backup to complete`() = runBlocking {
        withNodes { primary, backup ->
            primary.delayedStage = Stage.Connection
            primary.delayMillis = 800
            val events = repository(primary, backup, customLimits = limits.copy(primaryMillis = 150)).events()
            assertEquals("BBB", events.result().server.node)
            assertEquals(2, events.progress().count { it.phase == SpeedTestPhase.Connecting })
            assertEquals(1, primary.server.requestCount)
        }
    }

    @Test fun `whole test deadline is shared rather than restarted for the backup`() = runBlocking {
        withNodes { primary, backup ->
            listOf(primary, backup).forEach {
                it.delayedStage = Stage.Connection
                it.delayMillis = 2_000
            }
            val bounded = limits.copy(totalMillis = 1_000, primaryMillis = 450)
            val started = System.nanoTime()
            assertEquals(SpeedTestException.Reason.Timeout,
                repository(primary, backup, customLimits = bounded).failure())
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("shared 1000ms deadline took ${elapsedMillis}ms", elapsedMillis < 1_250)
            assertTrue("backup was actually attempted", backup.server.requestCount > 0)
        }
    }

    @Test fun `librespeed backup receives reserved whole MiB download and total request budgets stay bounded`() = runBlocking {
        withNodes(libreBackup = true) { primary, backup ->
            primary.failedStage = Stage.Upload
            val bounded = limits.copy(
                downloadBytes = 10 * MEBIBYTE,
                uploadBytes = 2 * MEBIBYTE,
                downloadChunkBytes = MEBIBYTE,
                downloadStreams = 1,
                minUploadChunkBytes = 128_000,
                maxUploadChunkBytes = 512_000,
            )
            val events = repository(primary, backup, customLimits = bounded).events()
            val result = events.result()
            assertEquals("LibreSpeed", result.server.provider)
            assertEquals("BBB", result.server.node)
            assertNull(result.testId)
            assertEquals(8 * MEBIBYTE, primary.downloadBytes.get())
            assertEquals(2 * MEBIBYTE, backup.downloadBytes.get())
            assertTrue(primary.downloadBytes.get() + backup.downloadBytes.get() <= bounded.downloadBytes)
            assertTrue(primary.uploadBytes.get() > 0)
            assertTrue(backup.uploadBytes.get() > 0)
            assertTrue(primary.uploadBytes.get() + backup.uploadBytes.get() <= bounded.uploadBytes)
            assertFalse(backup.downloadChunks.isEmpty())
            assertTrue(backup.downloadChunks.all { it >= 1 && it * MEBIBYTE <= bounded.downloadBytes })
            val reset = events.progress().filter { it.phase == SpeedTestPhase.Connecting }.last()
            assertEquals(SpeedTestMetrics(), reset.metrics)
            assertNull(reset.server)
        }
    }

    @Test fun `librespeed upload JSON error with HTTP 200 fails rather than completing the measurement`() = runBlocking {
        withNodes(libreBackup = true) { primary, backup ->
            primary.failedStage = Stage.Connection
            backup.uploadResponseBody = "{\"error\":\"upload rejected\"}"
            backup.uploadResponseType = "application/json"
            val events = ArrayList<SpeedTestEvent>()
            val bounded = limits.copy(downloadBytes = 2 * MEBIBYTE, downloadChunkBytes = MEBIBYTE)
            assertEquals(SpeedTestException.Reason.ServerError,
                repository(primary, backup, customLimits = bounded).failure(events))
            assertTrue(backup.uploadBytes.get() > 0)
            assertTrue(events.none { it is SpeedTestEvent.Completed })
        }
    }

    private fun repository(
        primary: NodeServer,
        backup: NodeServer,
        connectivity: FakeConnectivity = FakeConnectivity(),
        customLimits: SpeedTestLimits = limits,
    ) = HttpSpeedTest(connectivity, customLimits, listOf(primary.endpoint, backup.endpoint))

    private suspend fun HttpSpeedTest.events(): List<SpeedTestEvent> = withTimeout(8_000) { run().toList() }

    private suspend fun HttpSpeedTest.failure(events: MutableList<SpeedTestEvent> = ArrayList()): SpeedTestException.Reason {
        try { withTimeout(8_000) { run().toList(events) } } catch (error: SpeedTestException) { return error.reason }
        fail("Expected speed test failure")
        error("unreachable")
    }

    private fun List<SpeedTestEvent>.progress() = filterIsInstance<SpeedTestEvent.Progress>().map { it.progress }
    private fun List<SpeedTestEvent>.result() = (last() as SpeedTestEvent.Completed).result

    private suspend fun withNodes(libreBackup: Boolean = false, block: suspend (NodeServer, NodeServer) -> Unit) {
        NodeServer("AAA").use { primary -> NodeServer("BBB", libreBackup).use { backup -> block(primary, backup) } }
    }

    private enum class Stage { Connection, Download, Upload }

    private class FakeConnectivity(
        @Volatile var state: SpeedTestConnectivityState = SpeedTestConnectivityState.Online(1),
    ) : SpeedTestConnectivity {
        val switches = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override fun connectivity(): SpeedTestConnectivityState = state
        override fun switchedFrom(networkId: Long): Flow<Unit> = switches
    }

    /** 计数包含失败请求，证明预算按已发出的请求扣除，而不是只计算成功确认的流量。 */
    private class NodeServer(private val node: String, private val libre: Boolean = false) : Closeable {
        val server = MockWebServer()
        val downloadBytes = AtomicLong()
        val uploadBytes = AtomicLong()
        val downloadChunks = Collections.synchronizedList(ArrayList<Long>())
        @Volatile var failedStage: Stage? = null
        @Volatile var failureCode = 500
        @Volatile var delayedStage: Stage? = null
        @Volatile var delayMillis = 0L
        @Volatile var onRequest: ((Stage) -> Unit)? = null
        @Volatile var uploadResponseBody = ""
        @Volatile var uploadResponseType: String? = null
        val endpoint: SpeedTestEndpoint get() = if (libre)
            LibreSpeedEndpoint(server.url("/backend/").toString(), node)
        else CloudflareSpeedTestEndpoint(server.url("/backend/").toString())

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val stage = when {
                        request.method == "POST" -> Stage.Upload
                        path.endsWith("/__down") || path.endsWith("/garbage.php") -> Stage.Download
                        else -> Stage.Connection
                    }
                    val size = if (stage == Stage.Download) {
                        if (libre) request.requestUrl!!.queryParameter("ckSize")!!.toLong().also {
                            downloadChunks += it
                        } * MEBIBYTE else request.requestUrl!!.queryParameter("bytes")!!.toLong()
                    } else 0
                    if (stage == Stage.Download) downloadBytes.addAndGet(size)
                    if (stage == Stage.Upload) uploadBytes.addAndGet(request.bodySize)
                    onRequest?.invoke(stage)
                    val response = if (stage == failedStage) MockResponse().setResponseCode(failureCode) else when (stage) {
                        Stage.Connection -> if (libre) MockResponse().setHeader("Content-Type", "text/html").setBody("") else
                            MockResponse().setHeader("Content-Type", "text/plain").setHeader("cf-ray", "abc123-$node")
                                .setBody("fl=1\ncolo=$node\n")
                        Stage.Download -> MockResponse().setHeader("Content-Type", "application/octet-stream")
                            .setBody(Buffer().write(ByteArray(size.toInt())))
                        Stage.Upload -> MockResponse().setHeader("Content-Type",
                            uploadResponseType ?: if (libre) "text/html" else "text/plain").setBody(uploadResponseBody)
                    }
                    if (stage == delayedStage) response.setHeadersDelay(delayMillis, TimeUnit.MILLISECONDS)
                    return response
                }
            }
            server.start()
        }

        override fun close() = server.shutdown()
    }

    private companion object {
        const val MEBIBYTE = 1_048_576L
    }
}
