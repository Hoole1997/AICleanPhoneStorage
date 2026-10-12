package com.example.aicleanphonestorage.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.home.data.HomeBadge
import com.example.aicleanphonestorage.feature.home.data.HomeBadgeRepository
import com.example.aicleanphonestorage.feature.home.data.HomeCleaningSnapshot
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    repository: HomeOverviewRepository,
    cleaning: StateFlow<HomeCleaningSnapshot> = MutableStateFlow(HomeCleaningSnapshot()),
    private val badges: HomeBadgeRepository? = null,
) : ViewModel() {
    private var lastOverview: HomeOverview? = null
    private val refreshVersion = MutableStateFlow(0L)
    // 回到首页重新订阅时先沿用上次结果，避免 DataStore/远程读取完成前角标整体闪隐。
    private var lastBadges: Set<HomeBadge> = emptySet()
    private val visibleBadges =
        (badges?.observeVisible() ?: flowOf(emptySet()))
            .onEach { lastBadges = it }
            .onStart { emit(lastBadges) }

    val uiState =
        refreshVersion
            .flatMapLatest {
                repository
                    .observeOverview()
                    .map<HomeOverview, HomeUiState> {
                        lastOverview = it
                        HomeUiState.Ready(it)
                    }
                    .onStart {
                        emit(lastOverview?.let { HomeUiState.Ready(it) } ?: HomeUiState.Loading)
                    }
                    .catch { error ->
                        // 只转换可预期的数据错误。取消、编程错误和 OOM 不可伪装成普通业务失败。
                        when (error) {
                            is SecurityException ->
                                emit(HomeUiState.Failure(HomeUiState.Reason.PermissionRequired))
                            is IOException,
                            is android.database.SQLException ->
                                emit(HomeUiState.Failure(HomeUiState.Reason.StorageUnavailable))
                            else -> throw error
                        }
                    }
            }
            .combine(cleaning) { screen, snapshot ->
                if (screen is HomeUiState.Ready) screen.copy(cleaning = snapshot) else screen
            }
            .combine(visibleBadges) { screen, visible ->
                if (screen is HomeUiState.Ready) screen.copy(badges = visible) else screen
            }
            .stateIn(
                scope = viewModelScope,
                // 离开前台立即停止上游；重新订阅重新读取摘要，绝不能在这里自动执行扫描。
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                initialValue = HomeUiState.Loading,
            )

    /** 首页 onResume 调用：按设备本地日期判断是否跨过 0 点，前台跨天回到首页也会恢复角标。 */
    fun refreshBadges() {
        badges?.refreshDay()
    }

    /** 功能页真正打开后（含推送进入）隐藏该入口当日角标；未授权、取消或失败不调用。 */
    fun onFeatureEntered(badge: HomeBadge) {
        lastBadges = lastBadges - badge
        badges?.markEntered(badge)
    }

    fun retry() {
        // 只允许失败后重试，防止连续点击反复重启已在运行的读取操作。
        if (uiState.value is HomeUiState.Failure) refreshVersion.update { it + 1 }
    }
}
