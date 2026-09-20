package com.example.aicleanphonestorage.feature.battery.ui

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.data.OneShotTransfer
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.battery.data.BatterySnapshot

internal class BatteryEntryCoordinator(
    private val activity: AppCompatActivity,
    private val model: BatteryEntryViewModel,
    private val transfer: OneShotTransfer<BatterySnapshot>,
) {
    init {
        activity.supportFragmentManager.setFragmentResultListener(CANCEL, activity) { _, result ->
            model.cancel(result.getLong(TaskLoadingDialogFragment.REQUEST_ID))
        }
    }

    fun render(state: BatteryEntryState) {
        val manager = activity.supportFragmentManager
        if (
            manager.isStateSaved ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
            return
        val dialog = manager.findFragmentByTag(TAG) as? TaskLoadingDialogFragment
        if (state is BatteryEntryState.Loading) {
            val loading =
                LoadingUiState(
                    state.id,
                    activity.getString(R.string.battery_title),
                    activity.getString(R.string.battery_reading),
                    // 电池读取不展示阶段完成度，整个等待期间统一使用循环进度。
                    percent = null,
                    resultKey = CANCEL,
                    showPercentage = false,
                )
            if (dialog == null) TaskLoadingDialogFragment.newInstance(loading).showNow(manager, TAG)
            else dialog.render(loading)
        } else dialog?.dismiss()
        when (state) {
            is BatteryEntryState.Ready ->
                model.consume()?.let {
                    activity.startActivity(
                        Intent(activity, BatteryInfoActivity::class.java)
                            .putExtra(BatteryInfoActivity.EXTRA_SNAPSHOT, transfer.put(it))
                    )
                }
            BatteryEntryState.Failed -> {
                model.cancel()
                Toast.makeText(activity, R.string.battery_read_failed, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "battery.entry.loading"
        private const val CANCEL = "battery.entry.cancel"
    }
}
