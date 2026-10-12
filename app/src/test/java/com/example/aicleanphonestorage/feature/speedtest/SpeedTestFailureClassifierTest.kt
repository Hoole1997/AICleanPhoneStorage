package com.example.aicleanphonestorage.feature.speedtest

import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestConnectivityState
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException.Reason
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestFailureClassifier
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestHttpException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTestFailureClassifierTest {
    private val same = SpeedTestConnectivityState.Online(7)
    private fun classify(error: Throwable, now: SpeedTestConnectivityState = same) = SpeedTestFailureClassifier.classify(error, 7, now)

    @Test fun `system network state wins over the exception type`() {
        assertEquals(Reason.NoInternet, classify(SocketTimeoutException(), SpeedTestConnectivityState.Offline))
        assertEquals(Reason.NetworkChanged, classify(SocketTimeoutException(), SpeedTestConnectivityState.Online(8)))
        assertEquals(Reason.Restricted, classify(IOException(), SpeedTestConnectivityState.CaptivePortal))
        assertEquals(Reason.NetworkInfoUnavailable, classify(IOException(), SpeedTestConnectivityState.Unavailable))
    }

    @Test fun `exception types map to documented messages`() {
        assertEquals(Reason.Restricted, classify(SpeedTestHttpException(403)))
        assertEquals(Reason.Restricted, classify(SpeedTestHttpException(451)))
        assertEquals(Reason.ServerError, classify(SpeedTestHttpException(500)))
        assertEquals(Reason.ServerError, classify(SpeedTestHttpException(429)))
        assertEquals(Reason.Restricted, classify(SSLHandshakeException("intercepted")))
        assertEquals(Reason.Timeout, classify(SocketTimeoutException()))
        assertEquals(Reason.ServerError, classify(ProtocolException()))
        // 无法归类的连接错误按用户要求默认提示无网络。
        assertEquals(Reason.NoInternet, classify(UnknownHostException()))
        assertEquals(Reason.NoInternet, classify(ConnectException()))
    }
}
