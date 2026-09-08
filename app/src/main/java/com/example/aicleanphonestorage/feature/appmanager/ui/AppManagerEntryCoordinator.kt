package com.example.aicleanphonestorage.feature.appmanager.ui

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.data.OneShotTransfer
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.appmanager.data.AppManagerCatalog

internal class AppManagerEntryCoordinator(
    private val activity: AppCompatActivity,
    private val model: AppManagerEntryViewModel,
    private val transfer: OneShotTransfer<AppManagerCatalog>,
) {
    init {
        activity.supportFragmentManager.setFragmentResultListener(CANCEL, activity) { _, result ->
            model.cancel(result.getLong(TaskLoadingDialogFragment.REQUEST_ID))
        }
    }

    fun render(state: AppManagerEntryState) {
        val manager = activity.supportFragmentManager
        if (
            manager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val dialog = manager.findFragmentByTag(TAG) as? TaskLoadingDialogFragment
        if (state is AppManagerEntryState.Loading) {
            val detail = state.frame?.detail
            val message =
                if ((detail?.total ?: 0) > 0)
                    activity.getString(
                        R.string.app_manager_loading_apps,
                        detail?.completed ?: 0,
                        detail?.total ?: 0,
                    )
                else activity.getString(R.string.app_manager_loading_prepare)
            val loading =
                LoadingUiState(
                    state.id,
                    activity.getString(R.string.app_manager_scanning),
                    message,
                    state.frame?.percent,
                    resultKey = CANCEL,
                )
            if (dialog == null) TaskLoadingDialogFragment.newInstance(loading).showNow(manager, TAG)
            else dialog.render(loading)
        } else dialog?.dismiss()
        when (state) {
            is AppManagerEntryState.Ready ->
                model.consume()?.let {
                    activity.startActivity(
                        Intent(activity, AppManagerActivity::class.java)
                            .putExtra(AppManagerActivity.EXTRA_CATALOG, transfer.put(it))
                    )
                }
            AppManagerEntryState.Failed -> {
                model.cancel()
                Toast.makeText(activity, R.string.app_manager_load_failed, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "app_manager.entry.loading"
        private const val CANCEL = "app_manager.entry.cancel"
    }
}
