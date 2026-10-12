package com.example.aicleanphonestorage.feature.speedtest.data

import kotlinx.coroutines.flow.Flow

/** 测速按固定顺序推进；每个阶段的进度都来自真实耗时/字节数，不由动画时钟伪造。 */
enum class SpeedTestPhase { Connecting, Latency, Download, Upload }

/** 吞吐量统一用字节/秒保存，展示层再换算 KB/s；未测得时为 null，不用 0 冒充结果。 */
data class SpeedTestMetrics(
    val downloadBytesPerSecond: Double? = null,
    val uploadBytesPerSecond: Double? = null,
    val pingMs: Double? = null,
    val downloadJitterMs: Double? = null,
    val uploadJitterMs: Double? = null,
)

/** 测速节点：Cloudflare 只公开机房 IATA 代码（如 HKG），不返回城市或运营商。 */
data class SpeedTestServer(val provider: String, val node: String?)

enum class SpeedTestNetworkType { Wifi, Cellular, Ethernet, Other }

/** 本机网络概况。运营商名称只读系统公开值；Wi-Fi 名称需要位置权限，因此不读取。 */
data class SpeedTestNetwork(val type: SpeedTestNetworkType, val carrier: String?, val model: String)

data class SpeedTestProgress(
    val phase: SpeedTestPhase,
    /** 整体进度 0..1：连接与延迟 10%，下载、上传各 45%。 */
    val fraction: Float,
    /** 已完成阶段的结果与当前阶段的实时值。 */
    val metrics: SpeedTestMetrics,
    val server: SpeedTestServer?,
)

data class SpeedTestResult(
    val metrics: SpeedTestMetrics,
    val server: SpeedTestServer,
    /** 服务端为本次连接分配的 Ray ID，可用于向 Cloudflare 追溯请求；拿不到时为 null。 */
    val testId: String?,
)

sealed interface SpeedTestEvent {
    data class Progress(val progress: SpeedTestProgress) : SpeedTestEvent
    data class Completed(val result: SpeedTestResult) : SpeedTestEvent
}

class SpeedTestException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NoInternet, Failed }
}

/** 冷流：收集即开始一次测速，取消收集立即中止全部连接。失败以 [SpeedTestException] 结束。 */
fun interface SpeedTestRepository {
    fun run(): Flow<SpeedTestEvent>
}
