package com.example.aicleanphonestorage.feature.speedtest.data

import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
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
    /** 整次测速总时限（正常约 25 秒、最坏约 45 秒）；超过按超时处理。 */
    val totalMillis: Long = 60_000,
)

/**
 * 基于 Cloudflare 公共测速端点（speed.cloudflare.com）的测速实现。
 *
 * - 延迟：复用连接请求边缘直接响应的 /cdn-cgi/trace，取 OkHttp 记录的“请求发出→响应头到达”间隔，
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
internal class CloudflareSpeedTest(
    private val connectivity: SpeedTestConnectivity,
    private val limits: SpeedTestLimits = SpeedTestLimits(),
    private val baseUrl: String = BASE_URL,
    // 下载 4 流 + 探测 1 个阻塞调用，再留余量给进度采样；不占用应用共享 IO 任务许可。
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8),
    private val clock: () -> Long = System::nanoTime,
    private val clientFactory: () -> OkHttpClient = ::defaultClient,
) : SpeedTestRepository {
    // OkHttpClient 初始化会加载 TLS 平台，首次测速时在 IO 线程创建，之后复用。
    private val client by lazy(clientFactory)

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
                    try { measure(client) } finally { watcher.cancel() }
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

    private suspend fun ProducerScope<SpeedTestEvent>.measure(http: OkHttpClient) {
        var metrics = SpeedTestMetrics()
        send(progress(SpeedTestPhase.Connecting, 0f, metrics, null))
        val trace = trace(http)
        val server = SpeedTestServer(PROVIDER, trace.colo)

        val pings = ArrayList<Double>(limits.latencySamples)
        val latencyStart = clock()
        for (index in 0 until limits.latencySamples) {
            pings += probe(http)
            metrics = metrics.copy(pingMs = SpeedTestMath.median(pings))
            send(progress(SpeedTestPhase.Latency, LATENCY_WEIGHT * (index + 1) / limits.latencySamples, metrics, server))
            if (clock() - latencyStart >= limits.latencyBudgetNanos) break
        }

        val download = transfer(http, upload = false) { live, fraction ->
            send(progress(SpeedTestPhase.Download, LATENCY_WEIGHT + TRANSFER_WEIGHT * fraction,
                metrics.copy(downloadBytesPerSecond = live), server))
        }
        metrics = metrics.copy(downloadBytesPerSecond = download.rate, downloadJitterMs = download.jitter)

        val upload = transfer(http, upload = true) { live, fraction ->
            send(progress(SpeedTestPhase.Upload, LATENCY_WEIGHT + TRANSFER_WEIGHT * (1 + fraction),
                metrics.copy(uploadBytesPerSecond = live), server))
        }
        metrics = metrics.copy(uploadBytesPerSecond = upload.rate, uploadJitterMs = upload.jitter)
        if (metrics.downloadBytesPerSecond == null || metrics.uploadBytesPerSecond == null)
            throw SpeedTestException(SpeedTestException.Reason.WeakNetwork)
        send(SpeedTestEvent.Completed(SpeedTestResult(metrics, server, trace.rayId)))
    }

    private fun progress(phase: SpeedTestPhase, fraction: Float, metrics: SpeedTestMetrics, server: SpeedTestServer?) =
        SpeedTestEvent.Progress(SpeedTestProgress(phase, fraction.coerceIn(0f, 1f), metrics, server))

    private class Trace(val colo: String?, val rayId: String?)

    private class PhaseResult(val rate: Double?, val jitter: Double?)

    private suspend fun trace(http: OkHttpClient): Trace =
        http.newCall(get("$baseUrl/cdn-cgi/trace")).executeCancellable { response ->
            response.requireSuccess()
            // 响应仅约 300 字节的 key=value 文本；CF-RAY 形如 "8f2c...-HKG"。
            val colo = response.body?.string().orEmpty().lineSequence()
                .firstOrNull { it.startsWith("colo=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
            Trace(colo, response.header("cf-ray")?.substringBefore('-')?.takeIf { it.isNotBlank() })
        }

    /** 单次往返延迟（毫秒）。 */
    private suspend fun probe(http: OkHttpClient): Double =
        http.newCall(get("$baseUrl/cdn-cgi/trace")).executeCancellable { response ->
            response.requireSuccess()
            response.body?.bytes()
            (response.receivedResponseAtMillis - response.sentRequestAtMillis).coerceAtLeast(0).toDouble()
        }

    private suspend fun transfer(
        http: OkHttpClient,
        upload: Boolean,
        report: suspend (live: Double?, fraction: Float) -> Unit,
    ): PhaseResult = coroutineScope {
        val budget = if (upload) limits.uploadBytes else limits.downloadBytes
        val remaining = AtomicLong(budget)
        val moved = AtomicLong()
        val start = clock()
        val workers = List(if (upload) limits.uploadStreams else limits.downloadStreams) {
            launch { if (upload) uploadWorker(http, remaining, moved) else downloadWorker(http, remaining, moved) }
        }
        val loaded = Collections.synchronizedList(ArrayList<Double>())
        val prober = launch {
            while (true) {
                delay(limits.probeIntervalMillis)
                // 负载下探测只用于抖动统计，单次失败不影响主测速。
                try { loaded += probe(http) } catch (error: IOException) { currentCoroutineContext().ensureActive() }
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

    private suspend fun downloadWorker(http: OkHttpClient, remaining: AtomicLong, moved: AtomicLong) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val size = remaining.reserve(limits.downloadChunkBytes)
            if (size <= 0) return
            http.newCall(get("$baseUrl/__down?bytes=$size")).executeCancellable { response ->
                response.requireSuccess()
                val stream = response.body?.byteStream() ?: throw IOException("Empty body")
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    moved.addAndGet(read.toLong())
                }
            }
        }
    }

    private suspend fun uploadWorker(http: OkHttpClient, remaining: AtomicLong, moved: AtomicLong) {
        val payload = ByteArray(BUFFER_BYTES)
        var chunk = limits.minUploadChunkBytes
        while (true) {
            val size = remaining.reserve(chunk)
            if (size <= 0) return
            val started = clock()
            val request = Request.Builder().url("$baseUrl/__up")
                .header("Cache-Control", "no-store")
                .post(ZeroBody(size, payload))
                .build()
            http.newCall(request).executeCancellable { response ->
                response.requireSuccess()
                response.body?.bytes()
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

    private fun get(url: String) =
        Request.Builder().url(url).header("Cache-Control", "no-store").build()

    companion object {
        const val PROVIDER = "Cloudflare"
        private const val BASE_URL = "https://speed.cloudflare.com"
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
internal fun AtomicLong.reserve(chunk: Long): Long {
    while (true) {
        val left = get()
        if (left <= 0) return 0
        val take = minOf(left, chunk)
        if (compareAndSet(left, left - take)) return take
    }
}

/**
 * 阻塞执行并保证协程取消时立即中止网络：监视协程在取消时调用 [Call.cancel]，
 * 正在阻塞的读写随即抛出，再统一转换为协程取消。监视协程立即启动并在 Default 上恢复，
 * 即使测速调度器的线程全部阻塞在套接字上，取消仍能及时送达。
 */
private suspend fun <T> Call.executeCancellable(block: (Response) -> T): T = coroutineScope {
    val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { cancel() }
    }
    try {
        execute().use(block)
    } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw error
    } finally {
        watcher.cancel()
    }
}

private fun Response.requireSuccess() {
    if (!isSuccessful) throw SpeedTestHttpException(code)
}
