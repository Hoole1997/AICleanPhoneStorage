package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot

/** 数据加载状态与扫描状态分开：读取摘要失败，不代表用户从未扫描。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val overview: HomeOverview, val cleaning: HomeCleaningSnapshot = HomeCleaningSnapshot()) : HomeUiState
    data class Failure(val reason: Reason) : HomeUiState

    enum class Reason { StorageUnavailable, PermissionRequired }
}
