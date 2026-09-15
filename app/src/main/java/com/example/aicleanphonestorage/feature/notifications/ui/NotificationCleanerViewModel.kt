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
    val completion: CompletionReport? = null,
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
                selected = saved.get<ArrayList<String>>("notification.draft")?.toSet() ?: initialCatalog?.initialSelected.orEmpty(),
                hasSavedChanges = saved.contains("notification.draft"),
            )
        )
    val state = current.asStateFlow()
    private var applied = initialCatalog?.initialSelected.orEmpty()
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
                            applied = packages
                            current.update {
                                it.copy(
                                    selected = if (it.hasSavedChanges) it.selected else packages,
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

    fun retry() {
        current.update { it.copy(phase = NotificationPhase.CheckingAccess) }
        onForeground()
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
        val value = state.value
        val app = value.catalog?.apps?.find { it.packageName == packageName } ?: return
        if (value.phase != NotificationPhase.Ready || !value.rulesLoaded || value.saving.isNotEmpty() ||
            (!app.installed && enabled) || (packageName in value.selected) == enabled) return
        val selection = if (enabled) value.selected + packageName else value.selected - packageName
        val changed = selection != applied
        if (changed) saved["notification.draft"] = ArrayList(selection) else saved.remove<ArrayList<String>>("notification.draft")
        // 勾选只改页面草稿，离开/取消不会提前清除通知。
        current.update { it.copy(selected = selection, hasSavedChanges = changed) }
    }

    fun commitSelection() {
        val value = state.value
        if (!value.hasSavedChanges || value.saving.isNotEmpty() || !value.rulesLoaded || value.phase != NotificationPhase.Ready) return
        val selected = value.selected.toSet()
        val changes = (applied + selected).associateWith { it in selected }
        current.update { it.copy(saving = changes) }
        viewModelScope.launch {
            try {
                repository.setSelection(selected)
                applied = selected
                saved.remove<ArrayList<String>>("notification.draft")
                current.update { it.copy(selected = selected, hasSavedChanges = false,
                    completion = CompletionReport(CompletionKind.NOTIFICATIONS, selected.size)) }
            } catch (error: CancellationException) { throw error }
            catch (_: SecurityException) { current.update { it.copy(phase = NotificationPhase.NeedsAccess, rulesLoaded = false) } }
            catch (_: IOException) { current.update { it.copy(saveError = it.saveError + 1) } }
            finally { current.update { it.copy(saving = emptyMap()) } }
        }
    }

    /** 只有用户确认后已成功落盘的结果才允许进入完成页。 */
    fun completionReport(): CompletionReport? = state.value.completion?.takeIf { state.value.saving.isEmpty() }

    fun completionPresented() {
        saved["notification.changed"] = false
        current.update { it.copy(hasSavedChanges = false, completion = null) }
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
