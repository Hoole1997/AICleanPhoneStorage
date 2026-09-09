package io.docview.push.controller

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** 只请求本机 MockWebServer，验证新版浏览器协议，不把测试 token 发往真实后端。 */
class TokenUploadProtocolTest {
    @Test fun sendsBrowserEndpointWithEncodedValuesAndOriginalSignatureHeader() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"code\":0}"))
            server.start()
            val request = TokenUploadProtocol.request(server.url("/").toString(), "token+A/B=?", "user-42", "com.example.target")
            OkHttpClient().newCall(request).execute().use { response ->
                assertTrue(TokenUploadProtocol.accepted(response.isSuccessful, response.body!!.string()))
            }
            val recorded = server.takeRequest()
            assertEquals("/browser/wnfree", recorded.requestUrl!!.encodedPath)
            assertEquals("token+A/B=?", recorded.requestUrl!!.queryParameter("wndk"))
            assertEquals("user-42", recorded.requestUrl!!.queryParameter("weid"))
            assertEquals("com.example.target", recorded.requestUrl!!.queryParameter("dfk"))
            assertEquals("GET", recorded.method)
            assertEquals("6ac1fa71a1fe4fd5e2ef800e045ec58c", recorded.getHeader("seg"))
            assertNull(recorded.getHeader("fgi")) // 旧版本签名头不应继续发送。
        }
    }

    @Test fun serverRejectionsAndMalformedResponsesAreNotAcknowledged() {
        assertFalse(TokenUploadProtocol.accepted(false, "{\"code\":0}"))
        assertFalse(TokenUploadProtocol.accepted(true, "{\"code\":500}"))
        assertFalse(TokenUploadProtocol.accepted(true, "not-json"))
        assertTrue(TokenUploadProtocol.accepted(true, ""))
        assertTrue(TokenUploadProtocol.accepted(true, "{\"ok\":true}"))
    }
}
