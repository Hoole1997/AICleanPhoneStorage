package com.example.aicleanphonestorage.feature.battery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.battery.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** 只保留最后一份小型摘要；最后一个 UI 订阅消失时立即停止系统监听，不设置后台宽限期。 */
internal class BatteryInfoViewModel(repository: BatteryRepository, initial: BatterySnapshot?) :
    ViewModel() {
    val state =
        repository
            .observe()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                initial ?: BatterySnapshot(),
            )
}
