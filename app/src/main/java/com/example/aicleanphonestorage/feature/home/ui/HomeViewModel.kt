package com.example.aicleanphonestorage.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.home.data.HomeOverview
import com.example.aicleanphonestorage.feature.home.data.HomeOverviewRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repository: HomeOverviewRepository) : ViewModel() {
    private var lastOverview: HomeOverview? = null
    private val refreshVersion = MutableStateFlow(0L)

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
            .stateIn(
                scope = viewModelScope,
                // 离开前台立即停止上游；重新订阅重新读取摘要，绝不能在这里自动执行扫描。
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                initialValue = HomeUiState.Loading,
            )

    fun retry() {
        // 只允许失败后重试，防止连续点击反复重启已在运行的读取操作。
        if (uiState.value is HomeUiState.Failure) refreshVersion.update { it + 1 }
    }
}
