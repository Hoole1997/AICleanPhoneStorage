package com.example.aicleanphonestorage.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.analytics.BusinessTelemetry
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal data class DeviceScanState(
    // 六个有界字段，无文件明细、Bitmap 或 Activity 引用。
    val values: List<DeviceInfoValue?> = List(DeviceInfoField.entries.size) { null },
    val engineFailed: Boolean = false,
    val savingFailed: Boolean = false,
    val continuing: Boolean = false,
    val requestAd: Boolean = false,
    val navigateHome: Boolean = false,
) {
    val complete get() = values.all { it != null } && !engineFailed
}

/** 扫描逐项真实完成；没有为视觉效果伪造进度、固定扫描时长或后台轮询。 */
internal class DeviceScanViewModel(
    saved: SavedStateHandle,
    private val source: DeviceInfoSource,
    private val store: NewUserFlowStore,
    private val telemetry: EventSink = BusinessTelemetry,
) : ViewModel() {
    // 旋转复用 ViewModel 不重启；系统恢复了旧任务但进程已死亡时必须重新走启动路由。
    val processRestored = saved.get<Boolean>(CREATED) == true
    private val current = MutableStateFlow(DeviceScanState())
    val state = current.asStateFlow()
    private var job: Job? = null
    private var exposed = false
    private var finishedReported = false
    private var clicked = false

    init { saved[CREATED] = true }

    fun foreground() {
        if (processRestored) return
        if (!exposed) {
            exposed = true
            telemetry.send(MetricEvent.NEW_USER_SCAN_EXPOSURE, emptyMap())
        }
        if (job?.isActive == true || current.value.complete || current.value.engineFailed || current.value.continuing) return
        job = viewModelScope.launch {
            for (field in DeviceInfoField.entries) {
                if (current.value.values[field.ordinal] != null) continue
                val value = try { source.read(field) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (field == DeviceInfoField.ENGINE) {
                        current.value = current.value.copy(engineFailed = true)
                        return@launch
                    }
                    DeviceInfoValue.Unknown
                }
                coroutineContext.ensureActive()
                current.value = current.value.copy(values = current.value.values.toMutableList().also { it[field.ordinal] = value })
            }
            if (!finishedReported) {
                finishedReported = true
                telemetry.send(MetricEvent.NEW_USER_SCAN_FINISH, emptyMap())
            }
        }
    }

    fun background() { job?.cancel(); job = null }

    fun retry() {
        if (!current.value.engineFailed) return
        current.value = current.value.copy(engineFailed = false)
        foreground()
    }

    fun startClean() {
        if (!current.value.complete || current.value.continuing) return
        current.value = current.value.copy(continuing = true, savingFailed = false)
        if (!clicked) {
            clicked = true
            telemetry.send(MetricEvent.NEW_USER_START_CLEAN_CLICK, emptyMap())
        }
        // DataStore 原子提交成功后才请求广告；杀进程不依赖任何 Activity 结束回调。
        viewModelScope.launch {
            try {
                store.interstitialStarted()
                current.value = current.value.copy(requestAd = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { current.value = current.value.copy(continuing = false, savingFailed = true) }
        }
    }

    fun takeAdRequest(): Boolean {
        if (!current.value.requestAd) return false
        current.value = current.value.copy(requestAd = false)
        return true
    }

    fun adFinished() { current.value = current.value.copy(navigateHome = true) }

    private companion object { const val CREATED = "device.scan.created" }
}
