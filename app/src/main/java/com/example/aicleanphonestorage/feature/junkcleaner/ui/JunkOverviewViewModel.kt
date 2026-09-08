package com.example.aicleanphonestorage.feature.junkcleaner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.feature.junkcleaner.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

internal data class JunkOverviewState(
    val snapshot: JunkSnapshot? = null,
    val failed: Boolean = false,
)

internal class JunkOverviewViewModel(repository: JunkSummaryRepository, scan: Long) : ViewModel() {
    val state =
        repository
            .snapshots(scan)
            .map { JunkOverviewState(it, it.handle == null) }
            .catch {
                if (it is CancellationException) throw it
                emit(JunkOverviewState(failed = true))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), JunkOverviewState())
}
