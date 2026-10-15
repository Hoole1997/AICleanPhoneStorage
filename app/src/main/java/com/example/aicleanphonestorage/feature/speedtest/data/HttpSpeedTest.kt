package com.example.aicleanphonestorage.feature.speedtest.data

import java.io.IOException
import java.net.ProtocolException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** 单次测速的资源上限。用户确认：下载 50MB、上传 20MB，每个传输阶段最长约 10 秒。 */
internal data class SpeedTestLimits(
    val downloadBytes: Long = 50_000_000,
    val uploadBytes: Long = 20_000_000,
    val phaseNanos: Long = TimeUnit.SECONDS.toNanos(10),
    val downloadStreams: Int = 4,
    val uploadStreams: Int = 3,
    val downloadChunkBytes: Long = 8_000_000,
    val minUploadChunkBytes: Long = 128_000,
    val maxUploadChunkBytes: Long = 4_000_000,
    val latencySamples: Int = 10,
    val latencyBudgetNanos: Long = TimeUnit.SECONDS.toNanos(4),
    val probeIntervalMillis: Long = 300,
    val sampleIntervalMillis: Long = 100,
    /** 阶段结束时传输量低于此值视为网络过弱，数据不足以给出可靠速度。 */
    val minTransferBytes: Long = 64_000,
    /** 两条路线共用总期限，不能因切换备用把等待时间翻倍。 */
    val totalMillis: Long = 60_000,
    /** 首路最多占用半轮时间，为备用保留完整测量的机会。 */
    val primaryMillis: Long = 30_000,
)

/**
 * 共用 HTTP 测速引擎；端点仅负责协议，按顺序优先主路线、异常后尝试一次备用。
 *
 * - 延迟：复用连接请求节点直接响应的探测端点，取 OkHttp 记录的“请求发出→响应头到达”间隔，
 *   不含 DNS/TCP/TLS 建连；Ping 取中位数。
 * - 下载/上传：强制 HTTP/1.1，使多个并发流各自占用 TCP 连接；按字节预算分块请求，
 *   到达时长或流量上限即取消。下载按实际收到的字节计数；上传按服务端已确认的请求计数，
 *   避免把仍在本机发送缓冲区中的数据算作已上传。
 * - 抖动：传输期间另一连接每 300ms 探测一次负载下延迟，取相邻样本差的平均值。
 * - 请求禁用压缩（Accept-Encoding: identity），保证计数的是线路上的真实字节。
 *
 * 并发受专用有界调度器限制；取消收集会取消所有 OkHttp Call，结束后释放连接池。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HttpSpeedTest(
    private val connectivity: SpeedTestConnectivity,
    private val limits: SpeedTestLimits = SpeedTestLimits(),
    private val endpoints: List<SpeedTestEndpoint> = listOf(
        CloudflareSpeedTestEndpoint(),
        LibreSpeedEndpoint("https://la.speedtest.clouvider.net/backend", "Los Angeles"),
    ),
    // 下载 4 流 + 探测 1 个阻塞调用，再留余量给进度采样；不占用应用共享 IO 任务许可。
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8),
    private val clock: () -> Long = System::nanoTime,
    private val clientFactory: () -> OkHttpClient = ::defaultClient,
) : SpeedTestRepository {
    init { require(endpoints.size in 1..2) }
    // OkHttpClient 初始化会加载 TLS 平台，首次测速时在 IO 线程创建，之后复用。
    private val client by lazy(clientFactory)
    // 系统 DNS 可能在取消后继续阻塞：有限待建连名额防止反复重测积累无限 DNS 调用。
    private val pendingConnections = Semaphore(12)

    override fun run(): Flow<SpeedTestEvent> = channelFlow {
        val network = when (val state = connectivity.connectivity()) {
            SpeedTestConnectivityState.Offline -> throw SpeedTestException(SpeedTestException.Reason.NoInternet)
            SpeedTestConnectivityState.CaptivePortal -> throw SpeedTestException(SpeedTestException.Reason.Restricted)
            SpeedTestConnectivityState.Unavailable -> throw SpeedTestException(SpeedTestException.Reason.NetworkInfoUnavailable)
            is SpeedTestConnectivityState.Online -> state.networkId
        }
        try {
            val finished = withTimeoutOrNull(limits.totalMillis) {
                coroutineScope {
                    // 默认网络一旦切换，混合两个网络的数据没有意义，立即中止整次测速。
                    val watcher = launch {
                        connectivity.switchedFrom(network).first()
                        throw SpeedTestException(SpeedTestException.Reason.NetworkChanged)
                    }
                    try { measureRoutes(client, network) } finally { watcher.cancel() }
                }
            }
            if (finished == null) throw SpeedTestException(SpeedTestException.Reason.Timeout)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SpeedTestException) {
            throw error
        } catch (error: IOException) {
            throw SpeedTestException(SpeedTestFailureClassifier.classify(error, network, connectivity.connectivity()), error)
        } finally {
            // 测速结束不保留空闲连接与线程，避免页面关闭后仍占用套接字。
            client.connectionPool.evictAll()
        }
    }.flowOn(dispatcher)

    private class ByteBudget(limits: SpeedTestLimits) {
        val download = AtomicLong(limits.downloadBytes)
        val upload = AtomicLong(limits.uploadBytes)
    }

    private suspend fun ProducerScope<SpeedTestEvent>.measureRoutes(http: OkHttpClient, network: Long) {
        val budget = ByteBudget(limits)
        for ((index, endpoint) in endpoints.withIndex()) {
            currentCoroutineContext().ensureActive()
            requireSameNetwork(network)
            val hasBackup = index < endpoints.lastIndex
            try {
                // 首路预留 20% 字节和至少半轮时间；失败的请求也消耗已领取预算，不重发翻倍。
                val finished = withTimeoutOrNull(if (hasBackup)
                    minOf(limits.primaryMillis, (limits.totalMillis / 2).coerceAtLeast(1)) else limits.totalMillis) {
                    measure(http, endpoint, budget, hasBackup, network)
                    true
                }
                if (finished != true) throw SpeedTestException(SpeedTestException.Reason.Timeout)
                return
            } catch (cancelled: CancellationException) {
                // 页面离开前台或系统网络切换取消当前调用时，绝不能启动备用请求。
                throw cancelled
            } catch (error: SpeedTestException) {
                requireSameNetwork(network)
                if (!hasBackup || error.reason in setOf(SpeedTestException.Reason.NetworkChanged,
                        SpeedTestException.Reason.NetworkInfoUnavailable)) throw error
            } catch (error: IOException) {
                requireSameNetwork(network)
                if (!hasBackup) throw SpeedTestException(
                    SpeedTestFailureClassifier.classify(error, network, connectivity.connectivity()), error)
            } finally {
                http.connectionPool.evictAll()
            }
        }
    }

    /** 连接故障不等同于设备离线；只有同一可用网络上的路线错误才值得尝试备用。 */
    private fun requireSameNetwork(network: Long) {
        val reason = when (val state = connectivity.connectivity()) {
            SpeedTestConnectivityState.Offline -> SpeedTestException.Reason.NoInternet
            SpeedTestConnectivityState.CaptivePortal -> SpeedTestException.Reason.Restricted
            SpeedTestConnectivityState.Unavailable -> SpeedTestException.Reason.NetworkInfoUnavailable
            is SpeedTestConnectivityState.Online -> if (state.networkId != network)
                SpeedTestException.Reason.NetworkChanged else null
        }
        if (reason != null) throw SpeedTestException(reason)
    }

    private suspend fun ProducerScope<SpeedTestEvent>.measure(
        http: OkHttpClient, endpoint: SpeedTestEndpoint, budget: ByteBudget, reserveBackup: Boolean, network: Long,
    ) {
        // 每条路线独立创建指标；不把 CF 的下载与备用的上传拼成一个测速结果。
        var metrics = SpeedTestMetrics()
        send(progress(SpeedTestPhase.Connecting, 0f, metrics, null))
        val connection = http.newCall(endpoint.connectRequest()).executeCancellable(pendingConnections, endpoint::readConnection)
        val server = connection.server

        val pings = ArrayList<Double>(limits.latencySamples)
        val latencyStart = clock()
        for (index in 0 until limits.latencySamples) {
            pings += probe(http, endpoint)
            metrics = metrics.copy(pingMs = SpeedTestMath.median(pings))
            send(progress(SpeedTestPhase.Latency, LATENCY_WEIGHT * (index + 1) / limits.latencySamples, metrics, server))
            if (clock() - latencyStart >= limits.latencyBudgetNanos) break
        }

        val download = transfer(http, endpoint, budget.download, reserveBackup, upload = false) { live, fraction ->
            send(progress(SpeedTestPhase.Download, LATENCY_WEIGHT + TRANSFER_WEIGHT * fraction,
                metrics.copy(downloadBytesPerSecond = live), server))
        }
        metrics = metrics.copy(downloadBytesPerSecond = download.rate, downloadJitterMs = download.jitter)

        val upload = transfer(http, endpoint, budget.upload, reserveBackup, upload = true) { live, fraction ->
            send(progress(SpeedTestPhase.Upload, LATENCY_WEIGHT + TRANSFER_WEIGHT * (1 + fraction),
                metrics.copy(uploadBytesPerSecond = live), server))
        }
        metrics = metrics.copy(uploadBytesPerSecond = upload.rate, uploadJitterMs = upload.jitter)
        if (metrics.downloadBytesPerSecond == null || metrics.uploadBytesPerSecond == null)
            throw SpeedTestException(SpeedTestException.Reason.WeakNetwork)
        requireSameNetwork(network)
        send(SpeedTestEvent.Completed(SpeedTestResult(metrics, server, connection.testId)))
    }

    private fun progress(phase: SpeedTestPhase, fraction: Float, metrics: SpeedTestMetrics, server: SpeedTestServer?) =
        SpeedTestEvent.Progress(SpeedTestProgress(phase, fraction.coerceIn(0f, 1f), metrics, server))

    private class PhaseResult(val rate: Double?, val jitter: Double?)

    /** 单次往返延迟（毫秒）。 */
    private suspend fun probe(http: OkHttpClient, endpoint: SpeedTestEndpoint): Double =
        http.newCall(endpoint.probeRequest()).executeCancellable(pendingConnections) { response ->
            endpoint.readConnection(response)
            (response.receivedResponseAtMillis - response.sentRequestAtMillis).coerceAtLeast(0).toDouble()
        }

    private suspend fun transfer(
        http: OkHttpClient,
        endpoint: SpeedTestEndpoint,
        sharedBudget: AtomicLong,
        reserveBackup: Boolean,
        upload: Boolean,
        report: suspend (live: Double?, fraction: Float) -> Unit,
    ): PhaseResult = coroutineScope {
        val budget = sharedBudget.get().let { if (reserveBackup) it * 4 / 5 else it }
        if (budget <= 0) throw SpeedTestException(SpeedTestException.Reason.WeakNetwork)
        val remaining = AtomicLong(budget)
        val moved = AtomicLong()
        val start = clock()
        val workers = List(if (upload) limits.uploadStreams else limits.downloadStreams) {
            launch { if (upload) uploadWorker(http, endpoint, remaining, sharedBudget, moved)
                else downloadWorker(http, endpoint, remaining, sharedBudget, moved) }
        }
        val loaded = Collections.synchronizedList(ArrayList<Double>())
        val prober = launch {
            while (true) {
                delay(limits.probeIntervalMillis)
                // 负载下探测只用于抖动统计，单次失败不影响主测速。
                try { loaded += probe(http, endpoint) } catch (error: IOException) { currentCoroutineContext().ensureActive() }
            }
        }
        val samples = arrayListOf(TransferSample(0, 0))
        while (workers.any { it.isActive }) {
            delay(limits.sampleIntervalMillis)
            val elapsed = clock() - start
            samples += TransferSample(elapsed, moved.get())
            if (elapsed >= limits.phaseNanos) workers.forEach { it.cancel() }
            val fraction = maxOf(elapsed.toFloat() / limits.phaseNanos, moved.get().toFloat() / budget)
            report(SpeedTestMath.liveRate(samples, LIVE_WINDOW_NANOS), fraction.coerceIn(0f, 1f))
        }
        prober.cancel()
        // 时限内连可靠计算的最低数据量都没有传完：网络可用但过弱，不给出失真的速度。
        if (moved.get() < minOf(limits.minTransferBytes, budget)) throw SpeedTestException(SpeedTestException.Reason.WeakNetwork)
        samples += TransferSample(clock() - start, moved.get())
        PhaseResult(
            SpeedTestMath.sustainedRate(samples, WARMUP_FRACTION, MAX_WARMUP_NANOS),
            SpeedTestMath.jitter(synchronized(loaded) { loaded.toList() }),
        )
    }

    private suspend fun downloadWorker(http: OkHttpClient, endpoint: SpeedTestEndpoint,
        remaining: AtomicLong, sharedBudget: AtomicLong, moved: AtomicLong) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val size = remaining.reserve(limits.downloadChunkBytes, endpoint.downloadQuantumBytes)
            if (size <= 0) return
            sharedBudget.addAndGet(-size)
            http.newCall(endpoint.downloadRequest(size)).executeCancellable(pendingConnections) { response ->
                response.requireMeasurementSuccess()
                val stream = response.body?.byteStream() ?: throw IOException("Empty body")
                var left = size
                while (left > 0) {
                    val read = stream.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (read < 0) throw ProtocolException("Incomplete measurement body")
                    moved.addAndGet(read.toLong())
                    left -= read
                }
            }
        }
    }

    private suspend fun uploadWorker(http: OkHttpClient, endpoint: SpeedTestEndpoint,
        remaining: AtomicLong, sharedBudget: AtomicLong, moved: AtomicLong) {
        val payload = ByteArray(BUFFER_BYTES)
        var chunk = limits.minUploadChunkBytes
        while (true) {
            val size = remaining.reserve(chunk)
            if (size <= 0) return
            sharedBudget.addAndGet(-size)
            val started = clock()
            val request = endpoint.uploadRequest(ZeroBody(size, payload))
            http.newCall(request).executeCancellable(pendingConnections) { response ->
                response.requireMeasurementSuccess()
                endpoint.validateUploadResponse(response)
                if (response.body?.source()?.request(8_193) == true)
                    throw ProtocolException("Oversized upload response")
            }
            moved.addAndGet(size)
            // 按上一块速度调整块大小，使每个请求约 0.5 秒完成：慢网进度细腻，快网减少请求开销。
            val seconds = (clock() - started).coerceAtLeast(1) / 1_000_000_000.0
            chunk = (size / seconds * 0.5).toLong().coerceIn(limits.minUploadChunkBytes, limits.maxUploadChunkBytes)
        }
    }

    /** 上传内容固定为零字节，复用同一缓冲区，不分配大块内存。 */
    private class ZeroBody(private val size: Long, private val payload: ByteArray) : RequestBody() {
        override fun contentType() = OCTET_STREAM
        override fun contentLength() = size
        override fun writeTo(sink: BufferedSink) {
            var left = size
            while (left > 0) {
                val count = minOf(left, payload.size.toLong()).toInt()
                sink.write(payload, 0, count)
                left -= count
            }
        }
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private const val LATENCY_WEIGHT = 0.1f
        private const val TRANSFER_WEIGHT = 0.45f
        private const val WARMUP_FRACTION = 0.25
        private val MAX_WARMUP_NANOS = TimeUnit.MILLISECONDS.toNanos(1_500)
        private val LIVE_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(1_500)
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectionPool(ConnectionPool(6, 30, TimeUnit.SECONDS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addNetworkInterceptor { chain ->
                    // 由应用显式声明后 OkHttp 不再透明解压，保证统计的是网络实际传输字节。
                    chain.proceed(chain.request().newBuilder().header("Accept-Encoding", "identity").build())
                }
                .build()
    }
}

/** 从剩余预算中原子领取至多 [chunk] 字节；预算耗尽返回 0。 */
internal fun AtomicLong.reserve(chunk: Long, quantum: Long = 1): Long {
    while (true) {
        val left = get()
        if (left <= 0) return 0
        val take = minOf(left, chunk) / quantum * quantum
        if (take <= 0) return 0
        if (compareAndSet(left, left - take)) return take
    }
}

/**
 * 建连异步等待，避免不可中断的系统 DNS 拖住阶段/整轮截止；迟到响应由取消回调关闭。
 * 正文仍在有限 IO 调度器读取，取消时通过独立 Default 监视协程关闭 Call 中止套接字读写。
 */
private suspend fun <T> Call.executeCancellable(pending: Semaphore, block: (Response) -> T): T = coroutineScope {
    val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { cancel() }
    }
    try {
        awaitResponse(pending).use(block)
    } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        watcher.cancel()
    }
}

private suspend fun Call.awaitResponse(pending: Semaphore): Response = suspendCancellableCoroutine { waiting ->
    if (!pending.tryAcquire()) {
        waiting.resumeWithException(ProtocolException("Measurement connection limit reached"))
        return@suspendCancellableCoroutine
    }
    val released = AtomicBoolean(false)
    fun release() { if (released.compareAndSet(false, true)) pending.release() }
    waiting.invokeOnCancellation { cancel() }
    try {
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                release()
                waiting.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                release()
                // 覆盖响应到达与调度恢复之间的取消窗口，不能遗留未消费的正文/套接字。
                waiting.resume(response) { _, lateResponse, _ -> lateResponse.close() }
            }
        })
    } catch (error: RuntimeException) {
        release()
        waiting.resumeWithException(error)
    }
}

private fun Response.requireMeasurementSuccess() {
    if (!isSuccessful) throw SpeedTestHttpException(code)
    val type = header("Content-Type").orEmpty().lowercase()
    // PHP 的 empty.php 可带 text/html 头但正文为空；拒绝实际 HTML 登录/拦截页，保留空确认响应。
    val hasHtmlBody = "text/html" in type &&
        (request.method == "GET" || body?.source()?.request(1) == true)
    if (hasHtmlBody || "application/json" in type && request.method == "GET" ||
        header("Content-Encoding")?.let { it != "identity" } == true)
        throw ProtocolException("Unexpected measurement body")
}
