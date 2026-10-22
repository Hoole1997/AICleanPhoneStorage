package com.example.aicleanphonestorage.feature.onboarding

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenDeviceScanBinding
import java.util.Locale

/** UI 只格式化小型快照；原版图标不换色、不拉伸，动画不参与数据完成判定。 */
internal class DeviceScanRenderer(private val binding: ScreenDeviceScanBinding) {
    private val rows = listOf(binding.deviceRow, binding.systemRow, binding.resolutionRow,
        binding.densityRow, binding.storageRow, binding.engineRow)
    private val titles = listOf(R.string.new_user_device, R.string.new_user_system, R.string.new_user_resolution,
        R.string.new_user_density, R.string.new_user_storage, R.string.new_user_engine)
    private val icons = listOf(R.drawable.new_user_device, R.drawable.new_user_system, R.drawable.new_user_resolution,
        R.drawable.new_user_density, R.drawable.new_user_storage, R.drawable.new_user_engine)
    private val rotations = mutableListOf<ObjectAnimator>()
    private var state = DeviceScanState()
    private var active = false
    private var focused = false
    private var animated: List<View> = emptyList()

    init {
        binding.scanScroll.setOnScrollChangeListener { _: androidx.core.widget.NestedScrollView, _: Int, _: Int, _: Int, _: Int -> updateMotion() }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateMotion() }
        rows.forEachIndexed { index, row ->
            row.itemTitle.setText(titles[index])
            row.itemIcon.setImageResource(icons[index])
        }
    }

    fun render(value: DeviceScanState) {
        state = value
        val context = binding.root.context
        rows.forEachIndexed { index, row ->
            val fieldValue = value.values[index]
            row.itemValue.text = when (fieldValue) {
                null -> context.getString(if (index == DeviceInfoField.ENGINE.ordinal) R.string.new_user_initializing else R.string.new_user_scanning)
                DeviceInfoValue.Unknown -> context.getString(R.string.new_user_unknown)
                DeviceInfoValue.Done -> context.getString(R.string.new_user_done)
                is DeviceInfoValue.Text -> fieldValue.value
                is DeviceInfoValue.Storage -> {
                    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
                    context.getString(R.string.new_user_storage_format,
                        String.format(locale, "%.1f", fieldValue.used / 1_000_000_000.0),
                        String.format(locale, "%.1f", fieldValue.total / 1_000_000_000.0))
                }
            }
            row.itemValue.setTextColor(ContextCompat.getColor(context,
                if (fieldValue == DeviceInfoValue.Done) R.color.new_user_done else R.color.new_user_value))
            row.itemStatus.setImageResource(if (fieldValue == null) R.drawable.new_user_loading else R.drawable.new_user_selected)
            if (index == DeviceInfoField.ENGINE.ordinal && value.engineFailed) {
                row.itemValue.setText(R.string.new_user_engine_failed)
                row.itemStatus.isVisible = false
            } else row.itemStatus.isVisible = true
        }
        binding.scanningRing.isVisible = !value.complete
        binding.completedRing.isVisible = value.complete
        binding.completedCheck.isVisible = value.complete
        binding.scanError.isVisible = value.savingFailed
        binding.scanError.setText(R.string.new_user_save_failed)
        binding.scanAction.isEnabled = !value.continuing && (value.complete || value.engineFailed)
        binding.scanAction.setText(when {
            value.engineFailed || value.savingFailed -> R.string.new_user_retry
            value.complete -> R.string.new_user_start
            else -> R.string.new_user_scanning
        })
        updateMotion()
    }

    fun resumed(value: Boolean) { active = value; updateMotion() }
    fun windowFocus(value: Boolean) { focused = value; updateMotion() }

    private fun updateMotion() {
        val targets = if (active && focused && ValueAnimator.areAnimatorsEnabled() && !state.engineFailed && !state.complete)
            (listOf(binding.scanningRing) + rows.filterIndexed { index, _ -> state.values[index] == null }.map { it.itemStatus })
                .filter { it.isShown && it.getGlobalVisibleRect(Rect()) }
        else emptyList()
        if (targets == animated) return
        stop()
        animated = targets
        targets.forEach { view ->
            rotations += ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
                duration = 1_200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stop() {
        rotations.forEach { it.cancel() }
        rotations.clear()
        animated.forEach { it.rotation = 0f }
        animated = emptyList()
    }

    fun dispose() { active = false; focused = false; stop() }
}
