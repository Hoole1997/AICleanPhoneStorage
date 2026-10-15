package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.CloudflareSpeedTestEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.LibreSpeedEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestEndpoint
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestHttpException
import java.net.ProtocolException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 端点只决定请求协议与连接回应，防止错误页被当作测速成功或分块取整突破流量预算。 */
class SpeedTestEndpointTest {
    private val cloudflare = CloudflareSpeedTestEndpoint("https://cf.example/backend/")
    private val libre = LibreSpeedEndpoint("https://backup.example/backend", "Backup node")
    private val payload = ByteArray(12).toRequestBody("application/octet-stream".toMediaType())

    @Test fun `cloudflare uses trace exact byte download and upload endpoints`() {
        assertEquals("https://cf.example/backend/cdn-cgi/trace", cloudflare.connectRequest().url.toString())
        assertEquals(cloudflare.connectRequest().url, cloudflare.probeRequest().url)
        assertEquals("https://cf.example/backend/__down?bytes=12345", cloudflare.downloadRequest(12_345).url.toString())
        assertEquals("https://cf.example/backend/__up", cloudflare.uploadRequest(payload).url.toString())
        assertEquals(1L, cloudflare.downloadQuantumBytes)
        assertEquals("GET", cloudflare.downloadRequest(1).method)
        assertEquals("POST", cloudflare.uploadRequest(payload).method)
        assertSame(payload, cloudflare.uploadRequest(payload).body)
    }

    @Test fun `librespeed uses integer MiB garbage download and empty upload endpoints`() {
        assertEquals("https://backup.example/backend/empty.php", libre.connectRequest().url.toString())
        assertEquals(libre.connectRequest().url, libre.probeRequest().url)
        assertEquals("https://backup.example/backend/garbage.php?ckSize=3", libre.downloadRequest(3 * MEBIBYTE).url.toString())
        assertEquals("https://backup.example/backend/empty.php", libre.uploadRequest(payload).url.toString())
        assertEquals(MEBIBYTE, libre.downloadQuantumBytes)
        assertEquals("POST", libre.uploadRequest(payload).method)
        assertSame(payload, libre.uploadRequest(payload).body)
        listOf(-1L, 0L, 1L, MEBIBYTE - 1, MEBIBYTE + 1, MEBIBYTE * 1025).forEach { bytes ->
            assertThrows<IllegalArgumentException> { libre.downloadRequest(bytes) }
        }
        assertThrows<IllegalArgumentException> { cloudflare.downloadRequest(0) }
    }

    @Test fun `all endpoint requests disable cache without replacing the upload body`() {
        listOf<SpeedTestEndpoint>(cloudflare, libre).forEach { endpoint ->
            listOf(endpoint.connectRequest(), endpoint.probeRequest(), endpoint.downloadRequest(MEBIBYTE), endpoint.uploadRequest(payload))
                .forEach { request -> assertEquals("no-store", request.header("Cache-Control")) }
        }
    }

    @Test fun `cloudflare keeps only a valid public node and optional ray id`() {
        response(cloudflare, "fl=123\nip=192.0.2.1\ncolo=HKG\nloc=HK\n", ray = "8f2c1a-HKG").use {
            val connection = cloudflare.readConnection(it)
            assertEquals("Cloudflare", connection.server.provider)
            assertEquals("HKG", connection.server.node)
            assertEquals("8f2c1a", connection.testId)
        }
        response(cloudflare, "colo=TEST-12\n").use { assertNull(cloudflare.readConnection(it).testId) }
        response(cloudflare, "colo=HKG\n", ray = "bad ray ID-HKG").use {
            assertNull(cloudflare.readConnection(it).testId)
        }
    }

    @Test fun `cloudflare rejects missing invalid duplicate trace and HTML error pages`() {
        listOf("", "fl=1\n", "colo=\n", "colo=${"A".repeat(17)}\n", "colo=HKG<script>\n",
            "colo=HKG\ncolo=SIN\n", "<html>Login required</html>\ncolo=HKG\n").forEach { body ->
            response(cloudflare, body).use { assertThrows<ProtocolException> { cloudflare.readConnection(it) } }
        }
        response(cloudflare, "colo=HKG\n", mediaType = "text/html").use {
            assertThrows<ProtocolException> { cloudflare.readConnection(it) }
        }
        // OkHttp 禁止 close 没有 body 的合成响应；这里只验证缺失正文的协议错误，不创建套接字。
        val missingBody = response(cloudflare, "ignored", code = 204).newBuilder().body(null).build()
        assertThrows<ProtocolException> { cloudflare.readConnection(missingBody) }
    }

    @Test fun `cloudflare rejects overlong known and unknown length trace with bounded reads`() {
        response(cloudflare, "colo=HKG\n" + "x".repeat(8 * 1024)).use {
            assertThrows<ProtocolException> { cloudflare.readConnection(it) }
        }
        val source = Buffer().writeUtf8("colo=HKG\n" + "x".repeat(32 * 1024))
        val body = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength(): Long = -1
            override fun source() = source
        }
        response(cloudflare, "").newBuilder().body(body).build().use {
            val initial = source.size
            assertThrows<ProtocolException> { cloudflare.readConnection(it) }
            assertTrue("unknown response must not be read without a bound", initial - source.size <= 8 * 1024 + 1)
        }
    }

    @Test fun `librespeed accepts empty 200 and 204 without inventing test id`() {
        listOf(200, 204).forEach { code ->
            response(libre, "", code).use {
                val connection = libre.readConnection(it)
                assertEquals("LibreSpeed", connection.server.provider)
                assertEquals("Backup node", connection.server.node)
                assertNull(connection.testId)
            }
        }
        val unnamed = LibreSpeedEndpoint("https://backup.example/", null, "Operator")
        response(unnamed, "").use {
            assertEquals("Operator", unnamed.readConnection(it).server.provider)
            assertNull(unnamed.readConnection(it).server.node)
        }
    }

    @Test fun `librespeed rejects nonempty known or unknown response including HTML 200`() {
        response(libre, "<html>Sign in</html>", mediaType = "text/html").use {
            assertThrows<ProtocolException> { libre.readConnection(it) }
        }
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength(): Long = -1
            override fun source() = Buffer().writeUtf8("x")
        }
        response(libre, "").newBuilder().body(body).build().use {
            assertThrows<ProtocolException> { libre.readConnection(it) }
        }
    }

    @Test fun `both endpoint connections preserve non successful HTTP code`() {
        listOf<SpeedTestEndpoint>(cloudflare, libre).forEach { endpoint ->
            listOf(302, 403, 429, 500).forEach { code ->
                response(endpoint, "", code).use { response ->
                    val error = assertThrows<SpeedTestHttpException> { endpoint.readConnection(response) }
                    assertEquals(code, error.code)
                }
            }
        }
    }

    @Test fun `librespeed upload accepts only empty successful confirmation regardless of content type`() {
        listOf(200, 204).forEach { code ->
            response(libre, "", code, mediaType = "text/html").use { libre.validateUploadResponse(it) }
        }
        listOf("text/plain" to "error", "application/json" to "{\"error\":\"blocked\"}",
            "text/html" to "<html>Error</html>").forEach { (mediaType, body) ->
            response(libre, body, mediaType = mediaType).use {
                assertThrows<ProtocolException> { libre.validateUploadResponse(it) }
            }
        }
        response(libre, "", code = 403).use {
            assertEquals(403, assertThrows<SpeedTestHttpException> { libre.validateUploadResponse(it) }.code)
        }
        // CF 的上传响应不是 trace，不能错误地套用 colo 或 LibreSpeed 空正文约束。
        response(cloudflare, "{\"ok\":true}", mediaType = "application/json").use {
            cloudflare.validateUploadResponse(it)
        }
    }

    @Test fun `base URL rejects query fragment or embedded credentials`() {
        listOf("https://test.example/?route=x", "https://test.example/#route", "https://user:pass@test.example/").forEach { base ->
            assertThrows<IllegalArgumentException> { CloudflareSpeedTestEndpoint(base) }
            assertThrows<IllegalArgumentException> { LibreSpeedEndpoint(base, null) }
        }
    }

    private fun response(
        endpoint: SpeedTestEndpoint,
        body: String,
        code: Int = 200,
        ray: String? = null,
        mediaType: String = "text/plain",
    ): Response = Response.Builder().request(endpoint.connectRequest()).protocol(Protocol.HTTP_1_1)
        .code(code).message("Test response").body(body.toResponseBody(mediaType.toMediaType()))
        .apply { if (ray != null) header("cf-ray", ray) }.build()

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try { block() } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
        }
        fail("Expected ${T::class.java.simpleName}")
        error("unreachable")
    }

    private companion object {
        const val MEBIBYTE = 1_048_576L
    }
}
