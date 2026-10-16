package com.example.aicleanphonestorage.feature.speedtest.analytics

import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.speedtest.data.SpeedTestException

/** 测速只上报协议事件与原因枚举，不上传运营商、设备、测速节点、Ray ID 或速度数据。 */
internal class SpeedTestTelemetry(private val sink: EventSink = BusinessTelemetry) {
    fun scanResult() = sink.send(MetricEvent.SPEED_SCAN_RESULT, emptyMap())
    fun scanBack() = sink.send(MetricEvent.SPEED_SCAN_BACK, emptyMap())
    fun resultShown() = sink.send(MetricEvent.SPEED_RESULT_SHOW, emptyMap())
    fun testAgain() = sink.send(MetricEvent.TEST_AGAIN_CLICK, emptyMap())

    /** 失败事件仅记录用户操作；弹框展示不发送，保证每条记录都有 reason 与 action。 */
    fun failureAction(reason: SpeedTestException.Reason?, action: SpeedTestFailureAction) =
        sink.send(MetricEvent.SPEED_SCAN_FAIL, mapOf("reason" to reasonValue(reason), "action" to action.wireValue))

    private fun reasonValue(reason: SpeedTestException.Reason?): String = when (reason) {
        SpeedTestException.Reason.NoInternet -> "no_internet"
        SpeedTestException.Reason.WeakNetwork -> "weak_network"
        SpeedTestException.Reason.Timeout -> "timeout"
        SpeedTestException.Reason.NetworkChanged -> "network_changed"
        SpeedTestException.Reason.Restricted -> "network_restricted"
        SpeedTestException.Reason.NetworkInfoUnavailable -> "network_info_failed"
        SpeedTestException.Reason.ServerError -> "server_error"
        null -> "unknown"
    }
}

/** action 只接受协议中的两种用户操作，曝光不能伪造为 Cancel/Retry。 */
internal enum class SpeedTestFailureAction(val wireValue: String) { Cancel("cancel"), Retry("retry") }
