package com.example.aicleanphonestorage.feature.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemSettingsEntryBinding

/** 原生图标、标题和值各自布局；窄屏/大字号允许值换到下一行，整行仍只有一个点击与朗读目标。 */
class SettingsItemView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(context, attrs, defStyleAttr) {
    private val binding = ItemSettingsEntryBinding.inflate(LayoutInflater.from(context), this)

    private enum class TextArrangement {
        TITLE_ONLY,
        INLINE,
        STACKED,
    }

    private var textArrangement: TextArrangement? = null

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        // ConstraintLayout 的求解器使用自身 minHeight，不能只设置 View.minimumHeight。
        minHeight = (48 * resources.displayMetrics.density).toInt()
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

    fun setValue(value: CharSequence?) {
        val text = value ?: ""
        if (binding.settingsItemValue.text == text) return
        binding.settingsItemValue.text = text
        binding.settingsItemValue.isVisible = text.isNotEmpty()
        contentDescription =
            listOf(binding.settingsItemTitle.text, text)
                .filter(CharSequence::isNotEmpty)
                .joinToString(", ")
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hasValue = binding.settingsItemValue.isVisible
        val density = resources.displayMetrics.density
        val icon = binding.settingsItemIcon.layoutParams as LayoutParams
        val arrow = binding.settingsItemArrow.layoutParams as LayoutParams
        val text = binding.settingsItemText.layoutParams as LayoutParams
        val available =
            MeasureSpec.getSize(widthMeasureSpec) -
                paddingLeft -
                paddingRight -
                icon.marginStart -
                icon.width -
                text.marginStart -
                text.marginEnd -
                arrow.width -
                arrow.marginEnd
        val desired =
            android.text.Layout.getDesiredWidth(
                binding.settingsItemTitle.text,
                binding.settingsItemTitle.paint,
            ) +
                android.text.Layout.getDesiredWidth(
                    binding.settingsItemValue.text,
                    binding.settingsItemValue.paint,
                ) +
                8 * density
        val arrangement =
            when {
                !hasValue -> TextArrangement.TITLE_ONLY
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
                    desired > available -> TextArrangement.STACKED
                else -> TextArrangement.INLINE
            }
        // 只在横排/换行模式变化时重建约束，避免重复测量导致不断 requestLayout。
        if (arrangement != textArrangement) {
            textArrangement = arrangement
            arrangeText(arrangement)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun arrangeText(arrangement: TextArrangement) {
        val title = binding.settingsItemTitle.id
        val value = binding.settingsItemValue.id
        val gap = (resources.displayMetrics.density * 8).toInt()
        val constraints =
            ConstraintSet().apply {
                clone(binding.settingsItemText)
                clear(title, ConstraintSet.END)
                clear(title, ConstraintSet.BOTTOM)
                clear(value, ConstraintSet.START)
                clear(value, ConstraintSet.TOP)
                connect(
                    title,
                    ConstraintSet.END,
                    if (arrangement == TextArrangement.INLINE) value else ConstraintSet.PARENT_ID,
                    if (arrangement == TextArrangement.INLINE) ConstraintSet.START
                    else ConstraintSet.END,
                    if (arrangement == TextArrangement.INLINE) gap else 0,
                )
                if (arrangement == TextArrangement.STACKED) {
                    constrainWidth(value, ConstraintSet.MATCH_CONSTRAINT)
                    connect(
                        value,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                    )
                    connect(
                        value,
                        ConstraintSet.TOP,
                        title,
                        ConstraintSet.BOTTOM,
                        (resources.displayMetrics.density * 6).toInt(),
                    )
                } else {
                    constrainWidth(value, ConstraintSet.WRAP_CONTENT)
                    connect(
                        title,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                    )
                    connect(value, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                }
                // ConstraintSet 的可见性默认取 clone 时状态，因此保持 value 的显示事实不被约束切换覆盖。
                setVisibility(
                    value,
                    if (arrangement == TextArrangement.TITLE_ONLY) View.GONE else View.VISIBLE,
                )
            }
        binding.settingsItemValue.textAlignment =
            if (arrangement == TextArrangement.STACKED) View.TEXT_ALIGNMENT_VIEW_START
            else View.TEXT_ALIGNMENT_VIEW_END
        constraints.applyTo(binding.settingsItemText)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }
}
