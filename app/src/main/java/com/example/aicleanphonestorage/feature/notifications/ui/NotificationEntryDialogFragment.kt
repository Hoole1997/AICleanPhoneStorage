package com.example.aicleanphonestorage.feature.notifications.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.aicleanphonestorage.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NotificationEntryDialogFragment : DialogFragment() {
    val permission: Boolean get() = requireArguments().getBoolean("permission")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(if (permission) R.string.notification_access_title else R.string.notification_title)
        .setMessage(if (permission) R.string.notification_access_message else R.string.notification_failed)
        .setPositiveButton(if (permission) R.string.notification_access_open else R.string.traffic_retry) { _, _ -> result(if (permission) "access" else "retry") }
        .setNegativeButton(android.R.string.cancel) { _, _ -> result("cancel") }.create()
    override fun onCancel(dialog: DialogInterface) { result("cancel"); super.onCancel(dialog) }
    private fun result(action: String) = parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("action", action) })
    companion object {
        const val TAG = "notification_entry.permission"
        const val RESULT = "notification_entry.permission.result"
        fun create(permission: Boolean) = NotificationEntryDialogFragment().apply { arguments = Bundle().apply { putBoolean("permission", permission) } }
    }
}
