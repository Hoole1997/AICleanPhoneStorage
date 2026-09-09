package io.docview.push.controller

import com.google.gson.JsonParser
import io.docview.push.BuildConfig
import io.docview.push.host.PushEnvironment
import io.docview.push.host.PushEventReporter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 来源的签名、参数名和路径完整保留；URL 正确编码，不打印 token、签名或完整请求 URL。 */
internal object TokenUploadProtocol {
    private const val SECRET_KEY = "kA4deAzg7YpWTSZPYYa7wWb7WQk8Z7V5"
    fun signature(token: String, userId: String, packageName: String): String {
        val params = sortedMapOf("wndk" to token, "weid" to userId, "dfk" to packageName)
        val canonical = SECRET_KEY + params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("MD5").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    fun request(baseUrl: String, token: String, userId: String, packageName: String): Request =
        Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegments("browser/wnfree")
            .addQueryParameter("wndk", token).addQueryParameter("weid", userId)
            .addQueryParameter("dfk", packageName).build())
            .header("seg", signature(token, userId, packageName)).get().build()

    fun accepted(httpSuccess: Boolean, body: String): Boolean {
        if (!httpSuccess) return false
        if (body.isBlank()) return true
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            !json.has("code") || json.get("code").asInt == 0
        } catch (_: RuntimeException) { false }
    }
}

/** 单消费者 + 最新 token 合并队列；复用 HTTP 客户端，有总超时，关闭响应，只保留最近一次成功指纹。 */
object TokenUploadCtrl {
    private val uploads = Channel<String>(Channel.CONFLATED)
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val prefs by lazy { PushEnvironment.context.getSharedPreferences("token_upload_status", 0) }
    @Volatile private var acknowledged = ""

    init {
        PushEnvironment.scope.launch {
            acknowledged = prefs.getString("last_success", "").orEmpty()
            for (token in uploads) {
                try { performUpload(token) }
                catch (error: java.io.IOException) {
                    io.docview.push.utils.Logger.w("Token upload failed: ${error.javaClass.simpleName}")
                    PushEventReporter.reportData("fcm_token_report_fail")
                }
                catch (_: IllegalArgumentException) { PushEventReporter.reportData("fcm_token_report_fail") }
            }
        }
    }

    fun uploadToken(token: String) {
        if (BuildConfig.REMOTE_PUSH_ENABLED && token.isNotBlank()) uploads.trySend(token)
    }

    private fun fingerprint(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest("${BuildConfig.FCM_URL}|${BuildConfig.FCM_PKG}|$token".toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun isTokenUploaded(token: String) = acknowledged == fingerprint(token)

    private fun performUpload(token: String) {
        if (isTokenUploaded(token)) return
        var userId = prefs.getString("uuuuuuuuii1212ld", null)
        if (userId.isNullOrBlank()) {
            userId = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
            if (!prefs.edit().putString("uuuuuuuuii1212ld", userId).commit()) return
        }
        val request = TokenUploadProtocol.request(BuildConfig.FCM_URL, token, userId, BuildConfig.FCM_PKG)
        client.newCall(request).execute().use { response ->
            // token 上传响应应很小；限制内存，异常大响应不计为成功。
            val source = response.body?.source()
            val oversized = source?.request(65_537) == true
            val body = if (oversized) "" else source?.readUtf8().orEmpty()
            if (!oversized && TokenUploadProtocol.accepted(response.isSuccessful, body)) {
                val key = fingerprint(token)
                if (prefs.edit().putString("last_success", key).commit()) acknowledged = key
                io.docview.push.utils.Logger.i("Token upload acknowledged (HTTP ${response.code})")
                PushEventReporter.reportData("fcm_token_report_suc")
            } else {
                io.docview.push.utils.Logger.w("Token upload rejected (HTTP ${response.code})")
                PushEventReporter.reportData("fcm_token_report_fail")
            }
        }
    }

    fun clearUploadStatus() {
        acknowledged = ""
        PushEnvironment.scope.launch { prefs.edit().remove("last_success").commit() }
    }
}
