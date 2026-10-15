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

/** 当前真实测速路线及节点；Cloudflare 使用机房代码，备用使用服务公开的节点名称。 */
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

/** 测速失败原因，与失败弹框文案一一对应（用户 2026-10-12 指定）。 */
class SpeedTestException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** 无可用网络；无法归类的连接错误也默认显示此文案。 */
        NoInternet,
        /** 网络可用但阶段内传输数据量不足，无法得出可靠结果。 */
        WeakNetwork,
        /** 建连/读写超时，或整次测速超过总时限。 */
        Timeout,
        /** 测速过程中系统默认网络切换到另一个网络。 */
        NetworkChanged,
        /** 强制门户（需登录的 Wi-Fi）、HTTP 401/403/407/451 或 TLS 握手被拦截。 */
        Restricted,
        /** 无法读取系统网络状态。 */
        NetworkInfoUnavailable,
        /** 服务端错误（5xx、429 及其他非预期响应）。 */
        ServerError,
    }
}

/** 服务端返回非 2xx。 */
class SpeedTestHttpException(val code: Int) : java.io.IOException("HTTP $code")

/** 测速开始时的系统网络状态。 */
sealed interface SpeedTestConnectivityState {
    data object Offline : SpeedTestConnectivityState
    /** 系统检测到强制门户，需要先在浏览器登录。 */
    data object CaptivePortal : SpeedTestConnectivityState
    /** [networkId] 为系统 Network 句柄，用于判断测速期间是否切换了网络。 */
    data class Online(val networkId: Long) : SpeedTestConnectivityState
    /** 系统网络服务不可用或拒绝访问。 */
    data object Unavailable : SpeedTestConnectivityState
}

/** 系统网络状态来源；只在测速期间注册回调，测试注入假实现。 */
interface SpeedTestConnectivity {
    fun connectivity(): SpeedTestConnectivityState

    /** 系统默认网络切换为 [networkId] 以外的网络时发射；收集期间才注册系统回调。 */
    fun switchedFrom(networkId: Long): Flow<Unit>
}

/** 冷流：收集即开始一次测速，取消收集立即中止全部连接。失败以 [SpeedTestException] 结束。 */
fun interface SpeedTestRepository {
    fun run(): Flow<SpeedTestEvent>
}
