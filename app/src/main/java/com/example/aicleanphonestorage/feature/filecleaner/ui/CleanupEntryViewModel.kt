package com.example.aicleanphonestorage.feature.filecleaner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.scan.AccessRequest
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface CleanupEntryState {
    data object Idle : CleanupEntryState

    data class Permission(val feature: CleanupFeature, val request: AccessRequest) :
        CleanupEntryState

    data class Awaiting(val feature: CleanupFeature) : CleanupEntryState

    data class Loading(
        val feature: CleanupFeature,
        val id: Long,
        val frame: TimedEntryProgress.Frame? = null,
    ) : CleanupEntryState

    data class Ready(val handle: ScanHandle) : CleanupEntryState

    data class Failed(val feature: CleanupFeature) : CleanupEntryState
}

internal class CleanupEntryViewModel(
    private val repository: FileScanRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val current =
        MutableStateFlow<CleanupEntryState>(
            saved
                .get<String>(KEY)
                ?.let { runCatching { CleanupFeature.valueOf(it) }.getOrNull() }
                ?.let { CleanupEntryState.Awaiting(it) } ?: CleanupEntryState.Idle
        )
    val state = current.asStateFlow()
    private var job: Job? = null
    private var sequence = 0L

    fun begin(feature: CleanupFeature) {
        if (current.value is CleanupEntryState.Loading) return
        job?.cancel()
        saved[KEY] = feature.name
        job =
            viewModelScope.launch {
                try {
                    val permission = repository.resolveAccess(feature)
                    if (permission.request != AccessRequest.NONE) {
                        current.value = CleanupEntryState.Permission(feature, permission.request)
                        return@launch
                    }
                    val id = ++sequence
                    current.value = CleanupEntryState.Loading(feature, id)
                    val handle =
                        TimedEntryLoader().load(
                            initialStage = "FILES",
                            finalStage = "FILES",
                            count = { it: ScanHandle -> it.scannedCount },
                            onFrame = {
                                if ((current.value as? CleanupEntryState.Loading)?.id == id)
                                    current.value = CleanupEntryState.Loading(feature, id, it)
                            },
                        ) { report ->
                            repository.scan(feature) {
                                report(TaskProgress("FILES", it.completed, it.total))
                            }
                        }
                    currentCoroutineContext().ensureActive()
                    if ((current.value as? CleanupEntryState.Loading)?.id == id)
                        current.value = CleanupEntryState.Ready(handle)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SecurityException) {
                    val request = repository.resolveAccess(feature).request
                    current.value =
                        if (request == AccessRequest.NONE) CleanupEntryState.Failed(feature)
                        else CleanupEntryState.Permission(feature, request)
                } catch (_: IOException) {
                    current.value = CleanupEntryState.Failed(feature)
                } catch (_: android.database.SQLException) {
                    current.value = CleanupEntryState.Failed(feature)
                }
            }
    }

    fun awaitPermission(feature: CleanupFeature) {
        job?.cancel()
        current.value = CleanupEntryState.Awaiting(feature)
    }

    fun onForeground() {
        (current.value as? CleanupEntryState.Awaiting)?.let { begin(it.feature) }
    }

    fun onBackground() {
        if (current.value is CleanupEntryState.Loading) cancel()
    }

    fun cancel() {
        job?.cancel()
        current.value = CleanupEntryState.Idle
        saved.remove<String>(KEY)
    }

    fun consume(): ScanHandle? {
        val handle = (current.value as? CleanupEntryState.Ready)?.handle ?: return null
        cancel()
        return handle
    }

    fun rememberTree(uri: String, feature: CleanupFeature) {
        viewModelScope.launch {
            repository.rememberTree(uri)
            begin(feature)
        }
    }

    companion object {
        private const val KEY = "cleanup.entry.feature"
    }
}
