package com.example.aicleanphonestorage.feature.speedtest.data

import java.net.ProtocolException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 网络错误到失败原因的映射，按优先级判断：先看出错时的系统网络状态（离线/已切换/强制门户），
 * 再看异常类型。无法归类的连接错误按用户要求默认显示“无网络”文案。
 */
internal object SpeedTestFailureClassifier {
    private val restrictedCodes = setOf(401, 403, 407, 451)

    fun classify(error: Throwable, startNetwork: Long, now: SpeedTestConnectivityState): SpeedTestException.Reason = when {
        now is SpeedTestConnectivityState.Offline -> SpeedTestException.Reason.NoInternet
        now is SpeedTestConnectivityState.Online && now.networkId != startNetwork -> SpeedTestException.Reason.NetworkChanged
        now is SpeedTestConnectivityState.CaptivePortal -> SpeedTestException.Reason.Restricted
        now is SpeedTestConnectivityState.Unavailable -> SpeedTestException.Reason.NetworkInfoUnavailable
        error is SpeedTestHttpException ->
            if (error.code in restrictedCodes) SpeedTestException.Reason.Restricted else SpeedTestException.Reason.ServerError
        // 证书/握手失败多见于防火墙或代理拦截；连接中途重置等其他 TLS 错误不归为受限。
        error is SSLHandshakeException || error is SSLPeerUnverifiedException -> SpeedTestException.Reason.Restricted
        error is SocketTimeoutException -> SpeedTestException.Reason.Timeout
        error is ProtocolException -> SpeedTestException.Reason.ServerError
        else -> SpeedTestException.Reason.NoInternet
    }
}
