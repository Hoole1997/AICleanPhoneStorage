package com.example.aicleanphonestorage.feature.notifications.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.ui.completion.CompletionKind
import com.example.aicleanphonestorage.core.ui.completion.CompletionReport
import com.example.aicleanphonestorage.core.ui.loading.TaskProgress
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryLoader
import com.example.aicleanphonestorage.core.ui.loading.TimedEntryProgress
import com.example.aicleanphonestorage.feature.notifications.data.NotificationAppsRepository
import com.example.aicleanphonestorage.feature.notifications.data.NotificationCatalog
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface NotificationPhase {
    data object Idle : NotificationPhase

    data object CheckingAccess : NotificationPhase

    data object NeedsAccess : NotificationPhase

    data object AwaitingAccess : NotificationPhase

    data class Loading(val id: Long, val frame: TimedEntryProgress.Frame? = null) :
        NotificationPhase

    data object Ready : NotificationPhase

    data object Failed : NotificationPhase
}

internal data class NotificationUiState(
    val phase: NotificationPhase,
    val catalog: NotificationCatalog? = null,
    val selected: Set<String> = emptySet(),
    val saving: Map<String, Boolean> = emptyMap(),
    val rulesLoaded: Boolean = false,
    val listenerConnected: Boolean = false,
    val saveError: Long = 0,
    val hasSavedChanges: Boolean = false,
)

/** 入口与页面共用数据契约；保存规则不触发Loading，不让开关变化造成整表刷新。 */
internal class NotificationCleanerViewModel(
    private val repository: NotificationAppsRepository,
    private val connected: Flow<Boolean>,
    private val saved: SavedStateHandle,
    private val entry: Boolean,
    initialCatalog: NotificationCatalog? = null,
    private val entryLoader: TimedEntryLoader = TimedEntryLoader(),
) : ViewModel() {
    private val current =
        MutableStateFlow(
            NotificationUiState(
                if (initialCatalog != null) NotificationPhase.Ready
                else if (entry && saved.get<Boolean>(PENDING) != true) NotificationPhase.Idle
                else NotificationPhase.CheckingAccess,
                initialCatalog,
                selected = initialCatalog?.initialSelected.orEmpty(),
                hasSavedChanges = saved.get<Boolean>("notification.changed") == true,
            )
        )
    val state = current.asStateFlow()
    private var id = 0L
    private var check: Job? = null
    private var load: Job? = null
    private var timeout: Job? = null
    private var rules: Job? = null

    fun beginEntry() {
        if (!entry || state.value.phase != NotificationPhase.Idle) return
        saved[PENDING] = true
        current.value = NotificationUiState(NotificationPhase.CheckingAccess)
        onForeground()
    }

    fun onForeground() {
        if (entry && state.value.phase == NotificationPhase.Idle) return
        check?.cancel()
        check =
            viewModelScope.launch {
                try {
                    val granted = repository.hasAccess()
                    currentCoroutineContext().ensureActive()
                    if (!granted) {
                        load?.cancel()
                        timeout?.cancel()
                        rules?.cancel()
                        rules = null
                        current.update {
                            it.copy(phase = NotificationPhase.NeedsAccess, rulesLoaded = false)
                        }
                    } else {
                        observeRules()
                        if (
                            state.value.phase in
                                listOf(
                                    NotificationPhase.CheckingAccess,
                                    NotificationPhase.NeedsAccess,
                                    NotificationPhase.AwaitingAccess,
                                )
                        )
                            refresh()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SecurityException) {
                    current.update {
                        it.copy(phase = NotificationPhase.NeedsAccess, rulesLoaded = false)
                    }
                }
            }
    }

    private fun observeRules() {
        if (rules?.isActive == true) return
        rules =
            viewModelScope.launch {
                try {
                    combine(repository.selectedPackages, connected) { packages, running ->
                            packages to running
                        }
                        .collect { (packages, running) ->
                            current.update {
                                it.copy(
                                    selected = packages,
                                    rulesLoaded = true,
                                    listenerConnected = running,
                                )
                            }
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    current.update { it.copy(rulesLoaded = false, saveError = it.saveError + 1) }
                }
            }
    }

    fun refresh() {
        load?.cancel()
        timeout?.cancel()
        val request = ++id
        current.update { it.copy(phase = NotificationPhase.Loading(request)) }
        val deadline =
            viewModelScope.launch {
                delay(15_000)
                if ((state.value.phase as? NotificationPhase.Loading)?.id == request) {
                    load?.cancel()
                    current.update { it.copy(phase = NotificationPhase.Failed) }
                }
            }
        timeout = deadline
        load =
            viewModelScope.launch {
                try {
                    val catalog =
                        if (entry)
                            entryLoader.load(
                                count = { it: NotificationCatalog -> it.apps.size },
                                onStalled = { failLoading(request) },
                                onFrame = { frame ->
                                    current.update {
                                        if ((it.phase as? NotificationPhase.Loading)?.id == request)
                                            it.copy(
                                                phase = NotificationPhase.Loading(request, frame)
                                            )
                                        else it
                                    }
                                },
                            ) { report ->
                                repository.loadApps { done, total ->
                                    report(TaskProgress("APPLICATIONS", done, total))
                                }
                            }
                        else repository.loadApps { _, _ -> }
                    currentCoroutineContext().ensureActive()
                    current.update {
                        if ((it.phase as? NotificationPhase.Loading)?.id == request)
                            it.copy(phase = NotificationPhase.Ready, catalog = catalog)
                        else it
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SecurityException) {
                    current.update {
                        if ((it.phase as? NotificationPhase.Loading)?.id == request)
                            it.copy(phase = NotificationPhase.NeedsAccess)
                        else it
                    }
                } catch (_: IOException) {
                    current.update {
                        if ((it.phase as? NotificationPhase.Loading)?.id == request)
                            it.copy(phase = NotificationPhase.Failed)
                        else it
                    }
                } finally {
                    failLoading(request)
                    deadline.cancel()
                }
            }
    }

    private fun failLoading(request: Long) {
        current.update {
            if ((it.phase as? NotificationPhase.Loading)?.id == request)
                it.copy(phase = NotificationPhase.Failed) else it
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val state = state.value
        val app = state.catalog?.apps?.find { it.packageName == packageName } ?: return
        if (
            state.phase != NotificationPhase.Ready ||
                !state.rulesLoaded ||
                packageName in state.saving ||
                (!app.installed && enabled)
        )
            return
        if ((packageName in state.selected) == enabled) return
        current.update { it.copy(saving = it.saving + (packageName to enabled)) }
        viewModelScope.launch {
            try {
                repository.setEnabled(packageName, enabled)
                saved["notification.changed"] = true
                current.update {
                    it.copy(
                        selected =
                            if (enabled) it.selected + packageName else it.selected - packageName,
                        hasSavedChanges = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                current.update { it.copy(saveError = it.saveError + 1) }
            } finally {
                current.update { it.copy(saving = it.saving - packageName) }
            }
        }
    }

    /** 开关即时持久化；用户主动点击 Done 才汇总，绝不宣称已关闭系统通知权限。 */
    fun completionReport(): CompletionReport? {
        val value = state.value
        if (
            !value.hasSavedChanges ||
                value.saving.isNotEmpty() ||
                !value.rulesLoaded ||
                value.phase != NotificationPhase.Ready
        )
            return null
        return CompletionReport(CompletionKind.NOTIFICATIONS, value.selected.size)
    }

    fun completionPresented() {
        saved["notification.changed"] = false
        current.update { it.copy(hasSavedChanges = false) }
    }

    fun awaitAccess() {
        check?.cancel()
        load?.cancel()
        timeout?.cancel()
        current.update { it.copy(phase = NotificationPhase.AwaitingAccess) }
    }

    fun cancelEntry() {
        check?.cancel()
        load?.cancel()
        timeout?.cancel()
        rules?.cancel()
        rules = null
        saved[PENDING] = false
        current.value = NotificationUiState(NotificationPhase.Idle)
    }

    fun cancelLoading(requestId: Long) {
        val phase = state.value.phase
        if (
            (phase as? NotificationPhase.Loading)?.id == requestId ||
                (phase == NotificationPhase.CheckingAccess && requestId == 0L)
        )
            cancelEntry()
    }

    fun consumeCatalog(): NotificationCatalog? {
        if (!entry || state.value.phase != NotificationPhase.Ready) return null
        val value = state.value.catalog?.copy(initialSelected = state.value.selected) ?: return null
        cancelEntry()
        return value
    }

    fun onBackground() {
        check?.cancel()
        rules?.cancel()
        rules = null
        if (
            state.value.phase is NotificationPhase.Loading ||
                state.value.phase == NotificationPhase.CheckingAccess
        ) {
            if (entry) cancelEntry()
            else {
                load?.cancel()
                timeout?.cancel()
                current.update {
                    it.copy(
                        phase =
                            if (it.catalog != null) NotificationPhase.Ready
                            else NotificationPhase.Failed
                    )
                }
            }
        }
    }

    companion object {
        private const val PENDING = "notification_entry.pending"
    }
}
