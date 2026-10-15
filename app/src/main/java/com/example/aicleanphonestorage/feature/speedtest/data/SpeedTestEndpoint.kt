package com.example.aicleanphonestorage.feature.speedtest.data

import java.net.ProtocolException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/** 仅描述测速服务的请求协议；时限、流量、重试和生命周期统一由测量引擎管理。 */
internal interface SpeedTestEndpoint {
    val provider: String

    /** LibreSpeed 按整 MiB 返回下载数据；引擎须按此粒度领取预算，不能向服务端取整超额。 */
    val downloadQuantumBytes: Long get() = 1

    fun connectRequest(): Request
    fun readConnection(response: Response): SpeedTestConnection
    fun probeRequest(): Request
    fun downloadRequest(bytes: Long): Request
    fun uploadRequest(body: RequestBody): Request

    /** 上传确认由端点定义；Cloudflare 保留其公开响应协议，LibreSpeed 必须为空正文。 */
    fun validateUploadResponse(response: Response) = response.requireEndpointSuccess()
}

internal data class SpeedTestConnection(val server: SpeedTestServer, val testId: String?)

/** Cloudflare 保留公开边缘节点与可选 Ray ID，不把响应中的 IP 等无关信息带入状态。 */
internal class CloudflareSpeedTestEndpoint(baseUrl: String = "https://speed.cloudflare.com") : SpeedTestEndpoint {
    override val provider: String = "Cloudflare"
    private val baseUrl = directoryUrl(baseUrl)

    override fun connectRequest(): Request = request("cdn-cgi/trace").build()

    override fun probeRequest(): Request = connectRequest()

    override fun downloadRequest(bytes: Long): Request {
        require(bytes > 0) { "Download size must be positive" }
        return request("__down", "bytes", bytes.toString()).build()
    }

    override fun uploadRequest(body: RequestBody): Request = request("__up").post(body).build()

    override fun readConnection(response: Response): SpeedTestConnection {
        response.requireEndpointSuccess()
        val body = response.body ?: throw ProtocolException("Missing Cloudflare trace")
        if (body.contentType()?.subtype?.contains("html", ignoreCase = true) == true) {
            throw ProtocolException("Unexpected Cloudflare trace content")
        }
        // 即使代理返回 chunked/未知长度正文，也只读取上限 + 1 字节，避免 HTML 错误页或异常大响应。
        val trace = body.readBoundedTrace()
        val lines = trace.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty() || lines.any { !TRACE_KEY.matches(it.substringBefore('=')) || '=' !in it }) {
            throw ProtocolException("Invalid Cloudflare trace")
        }
        val nodes = lines.filter { it.startsWith("colo=") }.map { it.substringAfter('=') }
        val node = nodes.singleOrNull()?.takeIf { NODE.matches(it) }
            ?: throw ProtocolException("Missing Cloudflare node")
        // Ray ID 不是测速成功的必要条件；非预期头部不进入展示和日志。
        val ray = response.header("cf-ray")?.substringBefore('-')?.takeIf { RAY_ID.matches(it) }
        return SpeedTestConnection(SpeedTestServer(provider, node), ray)
    }

    private fun request(path: String, queryName: String? = null, queryValue: String? = null): Request.Builder =
        uncachedRequest(baseUrl, path, queryName, queryValue)

    private companion object {
        val NODE = Regex("[A-Za-z0-9-]{1,16}")
        val TRACE_KEY = Regex("[A-Za-z0-9_]+")
        val RAY_ID = Regex("[A-Za-z0-9]{1,64}")
    }
}

/**
 * LibreSpeed 公共后端协议：empty.php 同时承担连接探测、延迟和上传确认，garbage.php 按整 MiB 下载。
 * 备用节点由调用方明确配置，不从测速正文推断城市或伪造 Cloudflare Ray ID。
 */
internal class LibreSpeedEndpoint(
    baseUrl: String,
    private val node: String?,
    override val provider: String = "LibreSpeed",
) : SpeedTestEndpoint {
    override val downloadQuantumBytes: Long = MEBIBYTE
    private val baseUrl = directoryUrl(baseUrl)

    override fun connectRequest(): Request = uncachedRequest(baseUrl, "empty.php").build()

    override fun probeRequest(): Request = connectRequest()

    override fun downloadRequest(bytes: Long): Request {
        require(bytes >= MEBIBYTE && bytes % MEBIBYTE == 0L) { "LibreSpeed download size must use whole MiB" }
        // 官方后端最多接受 1024 块；拒绝超限而非静默改变请求量，保证引擎的预算与实际请求一致。
        val chunks = bytes / MEBIBYTE
        require(chunks <= 1024) { "LibreSpeed download size exceeds backend limit" }
        return uncachedRequest(baseUrl, "garbage.php", "ckSize", chunks.toString()).build()
    }

    override fun uploadRequest(body: RequestBody): Request = uncachedRequest(baseUrl, "empty.php").post(body).build()

    override fun readConnection(response: Response): SpeedTestConnection {
        response.requireEmptyLibreSpeedResponse()
        return SpeedTestConnection(SpeedTestServer(provider, node), null)
    }

    override fun validateUploadResponse(response: Response) = response.requireEmptyLibreSpeedResponse()

    private companion object {
        const val MEBIBYTE = 1_048_576L
    }
}

/** 测试可注入本机 HTTP 服务；生产只配置 HTTPS，不定制 TLS 或放宽证书验证。 */
private fun directoryUrl(baseUrl: String): HttpUrl = (baseUrl.trimEnd('/') + '/').toHttpUrl().also {
    require(it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty()) {
        "Speed test base URL must be a plain directory URL"
    }
}

private fun uncachedRequest(baseUrl: HttpUrl, path: String, queryName: String? = null, queryValue: String? = null): Request.Builder {
    val url = baseUrl.newBuilder().addPathSegments(path).apply {
        if (queryName != null) addQueryParameter(queryName, queryValue)
    }.build()
    return Request.Builder().url(url).header("Cache-Control", "no-store")
}

private fun Response.requireEndpointSuccess() {
    if (!isSuccessful) throw SpeedTestHttpException(code)
}

/** empty.php 的成功确认只有响应头；非空 JSON、文本或 HTML 错误页均不应计入已上传数据。 */
private fun Response.requireEmptyLibreSpeedResponse() {
    requireEndpointSuccess()
    val body = body
    if (body != null && (body.contentLength() > 0L || body.byteStream().read() != -1)) {
        throw ProtocolException("Expected empty LibreSpeed response")
    }
}

private fun okhttp3.ResponseBody.readBoundedTrace(): String {
    val maxBytes = 8 * 1024
    if (contentLength() > maxBytes) throw ProtocolException("Cloudflare trace exceeds limit")
    val buffer = ByteArray(maxBytes + 1)
    val input = byteStream()
    var count = 0
    while (count < buffer.size) {
        val read = input.read(buffer, count, buffer.size - count)
        if (read == -1) break
        if (read == 0) throw ProtocolException("Unreadable Cloudflare trace")
        count += read
    }
    if (count > maxBytes) throw ProtocolException("Cloudflare trace exceeds limit")
    return String(buffer, 0, count, Charsets.UTF_8)
}
