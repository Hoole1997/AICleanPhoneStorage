package com.example.aicleanphonestorage.feature.home.ui

import com.example.aicleanphonestorage.feature.home.data.HomeBadge
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot

/** 数据加载状态与扫描状态分开：读取摘要失败，不代表用户从未扫描。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val overview: HomeOverview,
        val cleaning: HomeCleaningSnapshot = HomeCleaningSnapshot(),
        /** 当前应显示角标的入口；最多十几个枚举值，不含文件明细。 */
        val badges: Set<HomeBadge> = emptySet(),
    ) : HomeUiState
    data class Failure(val reason: Reason) : HomeUiState

    enum class Reason { StorageUnavailable, PermissionRequired }
}
