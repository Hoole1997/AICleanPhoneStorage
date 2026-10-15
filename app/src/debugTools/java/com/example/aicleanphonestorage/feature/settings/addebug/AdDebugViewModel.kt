package com.example.aicleanphonestorage.feature.settings.addebug

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.ad.AdInitializationState
import com.example.aicleanphonestorage.app.ad.AdSdkInitializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class DebugAdFormat { APP_OPEN, INTERSTITIAL, REWARDED, FULL_SCREEN_NATIVE, NATIVE, BANNER }

internal data class AdDebugControls(val source: AdSource, val globalEnabled: Boolean)

/** 配置/诊断不依赖 Activity；广告 View 和 SDK 展示协程只属于独立预览页。 */
internal interface AdDebugSettings {
    suspend fun readControls(): AdDebugControls
    suspend fun setSource(source: AdSource)
    suspend fun setGlobalEnabled(enabled: Boolean)
    suspend fun resetDefaults()
    suspend fun diagnostics(format: DebugAdFormat, source: AdSource): String
}

internal data class AdDebugState(
    val source: AdSource = AdSource.BIDDING,
    val format: DebugAdFormat = DebugAdFormat.INTERSTITIAL,
    val globalEnabled: Boolean = true,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val sdkReady: Boolean = false,
    val diagnostics: String = "",
    val result: String = "",
)

internal data class AdDebugMessages(val operationFailed: String, val cancelled: String, val restored: String)

internal class AdDebugViewModel(
    private val saved: SavedStateHandle,
    private val settings: AdDebugSettings,
    private val sdkState: StateFlow<AdInitializationState> = AdSdkInitializer.state,
    private val messages: AdDebugMessages,
) : ViewModel() {
    private val current = MutableStateFlow(AdDebugState(format = saved.get<String>(FORMAT)
        ?.let { value -> DebugAdFormat.entries.firstOrNull { it.name == value } } ?: DebugAdFormat.INTERSTITIAL))
    val state = current.asStateFlow()
    private var initialized = false

    init {
        refresh()
        viewModelScope.launch {
            sdkState.collect { status ->
                current.update { it.copy(sdkReady = status == AdInitializationState.READY) }
                if (!current.value.loading && !current.value.busy) refresh()
            }
        }
    }

    fun selectSource(source: AdSource) {
        if (source == current.value.source) return
        change { settings.setSource(source) }
    }

    fun selectFormat(format: DebugAdFormat) {
        if (current.value.loading || current.value.busy || format == current.value.format) return
        saved[FORMAT] = format.name
        current.update { it.copy(format = format) }
        refresh()
    }

    fun setGlobalEnabled(enabled: Boolean) {
        if (enabled == current.value.globalEnabled) return
        change { settings.setGlobalEnabled(enabled) }
    }

    fun refresh() = change {}

    fun resetDefaults() = change {
        settings.resetDefaults()
        current.update { it.copy(result = messages.restored) }
    }

    /** 同一时刻只创建一个预览 Activity；暂停/失败后由 Activity Result 结束这次请求。 */
    fun beginShow(): Boolean {
        val value = current.value
        if (value.loading || value.busy || !value.sdkReady || !value.globalEnabled) return false
        current.update { it.copy(busy = true, result = "") }
        return true
    }

    fun finishShow(result: String) {
        current.update { it.copy(busy = false, result = result.take(MAX_MESSAGE_CHARS)) }
        refresh()
    }

    fun cancelShow() = finishShow(messages.cancelled)

    private fun change(operation: suspend () -> Unit) {
        if ((current.value.loading && initialized) || current.value.busy) return
        initialized = true
        current.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                operation()
                val controls = settings.readControls()
                // 配置读回成功就先发布真实控件值；诊断不可用不能让已生效的 SDK 开关看起来仍是旧值。
                current.update { it.copy(source = controls.source, globalEnabled = controls.globalEnabled,
                    sdkReady = sdkState.value == AdInitializationState.READY) }
                val detail = settings.diagnostics(current.value.format, controls.source)
                current.update { it.copy(diagnostics = "SDK: ${sdkState.value.name}\n${detail.take(MAX_DIAGNOSTIC_CHARS)}",
                    sdkReady = sdkState.value == AdInitializationState.READY, loading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // 错误状态不伪装成成功，也不输出配置值/凭据或无限增长的 SDK 堆栈。
                current.update { it.copy(loading = false, diagnostics = "SDK: ${sdkState.value.name}\n${messages.operationFailed}",
                    result = "${messages.operationFailed} (${error.javaClass.simpleName})") }
            }
        }
    }

    companion object {
        private const val FORMAT = "debug.ads.format"
        private const val MAX_MESSAGE_CHARS = 2_000
        private const val MAX_DIAGNOSTIC_CHARS = 6_000

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AdDebugViewModel(createSavedStateHandle(), SdkAdDebugSettings(), messages = AdDebugMessages(
                    application.getString(R.string.ad_debug_state_operation_failed),
                    application.getString(R.string.ad_debug_state_cancelled),
                    application.getString(R.string.ad_debug_state_restored),
                ))
            }
        }
    }
}
