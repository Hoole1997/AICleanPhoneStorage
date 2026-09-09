package com.example.aicleanphonestorage.feature.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemSettingsEntryBinding

/** 三列独立约束，不依赖 MaterialButton 内部 padding 或图标叠放；整行保持单一点击/朗读目标。 */
class SettingsItemView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {
    private val binding = ItemSettingsEntryBinding.inflate(LayoutInflater.from(context), this)

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        minimumHeight = (64 * resources.displayMetrics.density).toInt()
        isClickable = true
        isFocusable = true
        context.obtainStyledAttributes(attrs, R.styleable.SettingsItemView, defStyleAttr, 0).let {
            values ->
            try {
                binding.settingsItemTitle.text =
                    values.getText(R.styleable.SettingsItemView_settingTitle)
                binding.settingsItemIcon.setImageResource(
                    values.getResourceId(R.styleable.SettingsItemView_settingIcon, 0)
                )
            } finally {
                values.recycle()
            }
        }
        context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let {
            values ->
            try {
                background = values.getDrawable(0)
            } finally {
                values.recycle()
            }
        }
        contentDescription = binding.settingsItemTitle.text
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }
}
