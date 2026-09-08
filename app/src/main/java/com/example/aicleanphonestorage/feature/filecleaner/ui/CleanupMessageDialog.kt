package com.example.aicleanphonestorage.feature.filecleaner.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 对话框只发送小型结果，Activity 重建后仍由同一个 ViewModel 处理用户决定。 */
class CleanupMessageDialog : DialogFragment() {
    val identity: String
        get() = requireArguments().getString("identity").orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        if (identity != "entry") {
            val binding =
                com.example.aicleanphonestorage.databinding.DialogCleanupConfirmBinding.inflate(
                    layoutInflater
                )
            binding.confirmTitle.text = args.getString("title")
            binding.confirmMessage.text = args.getString("message")
            binding.confirmAccept.text = args.getString("positive")
            binding.confirmCancel.text = args.getString("negative")
            binding.confirmAccept.setOnClickListener {
                result("positive")
                dismiss()
            }
            binding.confirmCancel.setOnClickListener {
                result("negative")
                dismiss()
            }
            return Dialog(requireContext()).apply {
                setContentView(binding.root)
                setCanceledOnTouchOutside(false)
            }
        }
        val builder =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(args.getString("title"))
                .setMessage(args.getString("message"))
                .setPositiveButton(args.getString("positive")) { _, _ -> result("positive") }
                .setNegativeButton(args.getString("negative")) { _, _ -> result("negative") }
        args.getString("neutral")?.let {
            builder.setNeutralButton(it) { _, _ -> result("neutral") }
        }
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        if (identity != "entry")
            dialog?.window?.apply {
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                )
                val density = resources.displayMetrics.density
                setLayout(
                    minOf(
                        (315 * density).toInt(),
                        resources.displayMetrics.widthPixels - (48 * density).toInt(),
                    ),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.7f }
            }
    }

    override fun onCancel(dialog: DialogInterface) {
        result("negative")
        super.onCancel(dialog)
    }

    private fun result(action: String) =
        parentFragmentManager.setFragmentResult(
            RESULT,
            Bundle().apply {
                putString("identity", identity)
                putString("action", action)
            },
        )

    companion object {
        const val RESULT = "cleanup.message.result"
        const val TAG = "cleanup.message"

        fun create(
            identity: String,
            title: String,
            message: String,
            positive: String,
            negative: String,
            neutral: String? = null,
        ) =
            CleanupMessageDialog().apply {
                arguments =
                    Bundle().apply {
                        putString("identity", identity)
                        putString("title", title)
                        putString("message", message)
                        putString("positive", positive)
                        putString("negative", negative)
                        putString("neutral", neutral)
                    }
            }
    }
}
