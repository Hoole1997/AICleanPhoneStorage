package com.example.aicleanphonestorage.feature.retention

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import io.docview.push.NotificationDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RetentionPage { OFFER, REASON }
internal enum class RetentionFeature(val wire: String, val destination: NotificationDestination) {
    JUNK("junk_cleaner", NotificationDestination.CLEAN), MALWARE("malware_scan", NotificationDestination.MALWARE),
    SIMILAR("duplicate_photo_cleaner", NotificationDestination.DUPLICATE_PHOTOS),
    SCREENSHOTS("screenshot_cleaner", NotificationDestination.SCREENSHOTS), LARGE("large_file_cleaner", NotificationDestination.LARGE_FILES),
    NOTIFICATIONS("notification_cleaner", NotificationDestination.NOTIFICATION_CLEANER),
}
internal data class RetentionState(val page: RetentionPage = RetentionPage.OFFER, val busy: Boolean = false)

/** 反馈只在本地有界 SavedState 中恢复，不传给 Intent、日志、分析 SDK 或网络；无上传接口。 */
internal class RetentionViewModel(private val saved: SavedStateHandle, private val telemetry: EventSink = BusinessTelemetry) : ViewModel() {
    private val current = MutableStateFlow(RetentionState(
        if (saved.get<Boolean>(AD_PENDING) == true || saved.get<String>(PAGE) == RetentionPage.REASON.name) RetentionPage.REASON else RetentionPage.OFFER))
    val state = current.asStateFlow()
    init { saved[AD_PENDING] = false; saved[PAGE] = current.value.page.name }
    var reason: Int?
        get() = saved.get<Int>(REASON)?.takeIf { it in 0..5 }
        set(value) { saved[REASON] = value?.takeIf { it in 0..5 } }
    var feedback: String
        get() = saved.get<String>(TEXT).orEmpty()
        set(value) { saved[TEXT] = value.take(MAX_FEEDBACK) }

    fun shown() {
        val page = current.value.page
        if (saved.get<String>(SHOWN) == page.name) return
        saved[SHOWN] = page.name
        telemetry.send(if (page == RetentionPage.OFFER) MetricEvent.UNINSTALL_RETENTION_SHOW else MetricEvent.UNINSTALL_REASON_SHOW, emptyMap())
    }

    fun feature(feature: RetentionFeature) {
        telemetry.send(MetricEvent.UNINSTALL_FEATURE_CLICK, mapOf("feature_name" to feature.wire))
    }

    fun keep() = button("keep_app")
    fun uninstall() = button("uninstall")
    private fun button(action: String) {
        // 用户明确要求第三页选择与文本不上传：只记录操作，不附带 reason/has_other_content。
        telemetry.send(if (current.value.page == RetentionPage.OFFER) MetricEvent.UNINSTALL_RETENTION_CLICK else MetricEvent.UNINSTALL_REASON_CLICK,
            mapOf("action" to action))
    }

    fun beginAnyway(): Boolean {
        if (current.value.busy || current.value.page != RetentionPage.OFFER) return false
        uninstall()
        saved[AD_PENDING] = true
        current.value = current.value.copy(busy = true)
        return true
    }

    fun adFinished() {
        if (!current.value.busy) return
        saved[AD_PENDING] = false
        transition(RetentionPage.REASON)
    }

    fun back(): Boolean {
        if (current.value.busy || current.value.page == RetentionPage.OFFER) return false
        transition(RetentionPage.OFFER)
        return true
    }

    private fun transition(page: RetentionPage) {
        saved[PAGE] = page.name
        saved[SHOWN] = null
        current.value = RetentionState(page)
    }

    companion object {
        const val MAX_FEEDBACK = 500
        private const val PAGE = "retention.page"
        private const val SHOWN = "retention.shown"
        private const val AD_PENDING = "retention.ad.pending"
        private const val REASON = "retention.reason.local"
        private const val TEXT = "retention.text.local"
    }
}
