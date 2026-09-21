package com.example.aicleanphonestorage.feature.battery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.battery.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface BatteryEntryState {
    data object Idle : BatteryEntryState

    data class Loading(val id: Long, val frame: TimedEntryProgress.Frame? = null) :
        BatteryEntryState

    data class Ready(val snapshot: BatterySnapshot) : BatteryEntryState

    data object Failed : BatteryEntryState
}

/** 与现有入口共用加载时间轴。只保留小型摘要，取消/退后台后不跳转，不隐式恢复旧扫描。 */
internal class BatteryEntryViewModel(
    private val repository: BatteryRepository,
    private val loader: TimedEntryLoader = TimedEntryLoader(),
    private val telemetry: com.example.aicleanphonestorage.core.analytics.EventSink = com.example.aicleanphonestorage.core.analytics.BusinessTelemetry,
) : ViewModel() {
    private val current = MutableStateFlow<BatteryEntryState>(BatteryEntryState.Idle)
    val state = current.asStateFlow()
    private var work: Job? = null
    private var sequence = 0L

    fun begin() {
        if (current.value is BatteryEntryState.Loading || current.value is BatteryEntryState.Ready)
            return
        val id = ++sequence
        current.value = BatteryEntryState.Loading(id)
        work = viewModelScope.launch {
            try {
                val snapshot =
                    loader.load(
                        initialStage = "BATTERY",
                        finalStage = "BATTERY",
                        count = { _: BatterySnapshot -> 1 },
                        onFrame = {
                            if ((current.value as? BatteryEntryState.Loading)?.id == id)
                                current.value = BatteryEntryState.Loading(id, it)
                        },
                        onStalled = { fail(id) },
                    ) { report ->
                        repository.read().also { report(TaskProgress("BATTERY", 1, 1)) }
                    }
                ensureActive()
                if ((current.value as? BatteryEntryState.Loading)?.id == id) {
                    current.value = BatteryEntryState.Ready(snapshot)
                    telemetry.send(com.example.aicleanphonestorage.core.analytics.MetricEvent.BATTERYINFO_SCAN_RESULT, emptyMap())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fail(id)
            } finally {
                fail(id)
            }
        }
    }

    private fun fail(id: Long) {
        if ((current.value as? BatteryEntryState.Loading)?.id == id)
            current.value = BatteryEntryState.Failed
    }

    fun cancel() {
        work?.cancel()
        work = null
        current.value = BatteryEntryState.Idle
    }

    fun cancel(id: Long) {
        if ((current.value as? BatteryEntryState.Loading)?.id == id) cancel()
    }

    fun consume(): BatterySnapshot? {
        val value = (current.value as? BatteryEntryState.Ready)?.snapshot ?: return null
        current.value = BatteryEntryState.Idle
        return value
    }
}
