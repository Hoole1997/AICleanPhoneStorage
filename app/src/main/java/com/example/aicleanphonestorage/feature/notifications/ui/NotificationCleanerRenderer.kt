package com.example.aicleanphonestorage.feature.notifications.ui

import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.databinding.ScreenNotificationCleanerBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope

internal class NotificationCleanerRenderer(
    private val binding: ScreenNotificationCleanerBinding,
    scope: CoroutineScope,
    icons: AppIconLoader,
    toggle: (String, Boolean) -> Unit,
    retry: () -> Unit,
    connectionAction: () -> Unit,
) {
    private val adapter = NotificationAppsAdapter(scope, icons, toggle, connectionAction)
    private var lastSaveError = 0L

    init {
        binding.notificationList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.notificationList.adapter = adapter
        binding.notificationList.itemAnimator =
            DefaultItemAnimator().apply { supportsChangeAnimations = false }
        binding.notificationRetry.setOnClickListener { retry() }
    }

    fun render(state: NotificationUiState) {
        binding.notificationDone.isVisible = state.hasSavedChanges
        binding.notificationDone.isEnabled =
            state.saving.isEmpty() && state.rulesLoaded && state.phase == NotificationPhase.Ready
        binding.notificationList.isVisible = state.catalog != null
        binding.notificationErrorPanel.isVisible =
            state.catalog?.apps.isNullOrEmpty() && state.phase == NotificationPhase.Failed
        binding.notificationPageProgress.isVisible =
            state.phase is NotificationPhase.Loading ||
                state.phase == NotificationPhase.CheckingAccess
        adapter.submit(state)
        if (state.saveError > lastSaveError) {
            lastSaveError = state.saveError
            Snackbar.make(binding.root, R.string.notification_save_failed, Snackbar.LENGTH_LONG)
                .show()
        }
    }

    fun setActive(value: Boolean) = adapter.setActive(value)

    fun dispose() {
        adapter.setActive(false)
        binding.notificationList.adapter = null
    }
}
