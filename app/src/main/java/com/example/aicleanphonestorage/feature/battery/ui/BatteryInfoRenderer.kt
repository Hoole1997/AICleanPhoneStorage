package com.example.aicleanphonestorage.feature.battery.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ItemBatteryInfoBinding
import com.example.aicleanphonestorage.databinding.ScreenBatteryInfoBinding
import com.example.aicleanphonestorage.feature.battery.data.*
import java.text.NumberFormat

/** 六张轻量信息卡片按列组合；数值自然测量，容量数字和小字号单位由同一行文本基线对齐。 */
internal class BatteryInfoRenderer(private val binding: ScreenBatteryInfoBinding) {
    private val context = binding.root.context
    private val numbers =
        NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    private val decimals =
        NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    private val percentages =
        NumberFormat.getPercentInstance(context.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 0
        }
    private val unknown = context.getString(R.string.home_unknown_value)
    private val cards = ArrayList<ItemBatteryInfoBinding>(6)
    private var healthIcon = 0

    init {
        val definitions =
            listOf(
                R.drawable.battery_brightness to R.string.battery_brightness,
                R.drawable.battery_temperature to R.string.battery_temperature,
                R.drawable.battery_voltage to R.string.battery_voltage,
                R.drawable.battery_type to R.string.battery_type,
                R.drawable.battery_capacity to R.string.battery_capacity,
                R.drawable.battery_health_good to R.string.battery_health,
            )
        val columns = if (context.resources.configuration.fontScale >= 1.5f) 1 else 2
        val horizontal =
            context.resources.getDimensionPixelSize(R.dimen.battery_card_gap_horizontal)
        val vertical = context.resources.getDimensionPixelSize(R.dimen.battery_card_gap_vertical)
        definitions.chunked(columns).forEachIndexed { rowIndex, items ->
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
            binding.batteryCards.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = vertical },
            )
            items.forEachIndexed { column, (icon, label) ->
                val card = ItemBatteryInfoBinding.inflate(LayoutInflater.from(context), row, false)
                card.batteryInfoIcon.setImageResource(icon)
                card.batteryInfoLabel.setText(label)
                row.addView(
                    card.root,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (column > 0) marginStart = horizontal
                    },
                )
                cards += card
            }
        }
    }

    fun render(value: BatterySnapshot) {
        binding.batteryGauge.render(value.percent, value.charging, value.powerConnected)
        text(
            0,
            if (value.automaticBrightness) context.getString(R.string.battery_auto)
            else value.brightnessPercent?.let { percentages.format(it / 100.0) } ?: unknown,
        )
        text(
            1,
            value.temperatureTenthsC?.let {
                context.getString(R.string.battery_temperature_value, decimals.format(it / 10.0))
            } ?: unknown,
        )
        text(
            2,
            value.voltageMv?.let {
                context.getString(R.string.battery_voltage_value, numbers.format(it))
            } ?: unknown,
        )
        text(3, value.technology ?: unknown)
        val current = value.remainingMah?.let(numbers::format) ?: unknown
        val suffix =
            context.getString(
                R.string.battery_capacity_suffix,
                value.fullMah?.let(numbers::format) ?: unknown,
            )
        val capacity =
            SpannableString(current + suffix).apply {
                setSpan(
                    RelativeSizeSpan(12f / 18f),
                    current.length,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        text(4, capacity)
        cards[4].root.contentDescription =
            context.getString(
                R.string.battery_capacity_description,
                current,
                value.fullMah?.let(numbers::format) ?: unknown,
            )
        val health =
            when (value.health) {
                BatteryHealth.GOOD -> R.string.battery_good
                BatteryHealth.BAD -> R.string.battery_bad
                BatteryHealth.OVERHEATING -> R.string.battery_overheating
                BatteryHealth.OVERVOLTAGE -> R.string.battery_overvoltage
                BatteryHealth.COLD -> R.string.battery_cold
                BatteryHealth.UNKNOWN -> R.string.home_unknown_value
            }
        text(5, context.getString(health))
        val icon =
            when (value.health) {
                BatteryHealth.GOOD -> R.drawable.battery_health_good
                BatteryHealth.UNKNOWN -> R.drawable.battery_type
                else -> R.drawable.battery_health_bad
            }
        if (healthIcon != icon) {
            healthIcon = icon
            cards[5].batteryInfoIcon.setImageResource(icon)
        }
    }

    private fun text(index: Int, value: CharSequence) {
        val view = cards[index].batteryInfoValue
        if (view.text.toString() != value.toString()) view.text = value
    }
}
