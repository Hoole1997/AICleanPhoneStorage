package com.example.aicleanphonestorage.feature.networktraffic.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 权限说明和入口失败都留在首页，FragmentManager 负责旋转时恢复，业务由入口 ViewModel 控制。 */
class TrafficEntryDialogFragment : DialogFragment() {
    val kind: String get() = requireArguments().getString("kind").orEmpty()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(if (kind == USAGE) R.string.traffic_access_title else R.string.traffic_title)
        .setMessage(when (kind) { USAGE -> R.string.traffic_access_message; PHONE -> R.string.traffic_phone_needed; else -> R.string.traffic_failed })
        .setPositiveButton(when (kind) { USAGE -> R.string.traffic_access_action; PHONE -> R.string.traffic_phone_action; else -> R.string.traffic_retry }) { _, _ -> result(kind) }
        .setNegativeButton(if (kind == PHONE) R.string.traffic_wifi_only else android.R.string.cancel) { _, _ -> result(if (kind == PHONE) "skip_phone" else "cancel") }
        .create()
    override fun onCancel(dialog: DialogInterface) { result("cancel"); super.onCancel(dialog) }
    private fun result(action: String) = parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("action", action) })
    companion object {
        const val TAG = "traffic_entry_message"
        const val RESULT = "traffic_entry_message.result"
        const val USAGE = "usage"
        const val PHONE = "phone"
        const val RETRY = "retry"
        fun create(kind: String) = TrafficEntryDialogFragment().apply { arguments = Bundle().apply { putString("kind", kind) } }
    }
}
