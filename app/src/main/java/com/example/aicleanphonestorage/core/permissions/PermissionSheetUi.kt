package com.example.aicleanphonestorage.core.permissions

import android.graphics.Color
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.aicleanphonestorage.databinding.DialogAppPermissionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** 仅负责统一视觉与系统安全区。结果回调、权限类型和申请生命周期仍归各自协调器。 */
internal object PermissionSheetUi {
    fun prepare(binding: DialogAppPermissionBinding) {
        ViewCompat.setAccessibilityHeading(binding.permissionTitle, true)
        val panel = binding.permissionPanel
        val bottom = (34 * panel.resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            panel.setPadding(bars.left, panel.paddingTop, bars.right, maxOf(bottom, bars.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    fun show(dialog: BottomSheetDialog) {
        val metrics = dialog.context.resources.displayMetrics
        dialog.setCanceledOnTouchOutside(false)
        dialog.behavior.apply {
            maxWidth = (600 * metrics.density).toInt()
            maxHeight = (metrics.heightPixels * 0.92f).toInt()
            isDraggable = false
            skipCollapsed = true
            setShouldRemoveExpandedCorners(false)
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.window?.let { window ->
            window.setDimAmount(0.63f)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
                true
            if (Build.VERSION.SDK_INT < 27) window.navigationBarColor = Color.BLACK
        }
    }
}
