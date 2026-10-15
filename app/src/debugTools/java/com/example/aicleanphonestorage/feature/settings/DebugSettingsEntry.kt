package com.example.aicleanphonestorage.feature.settings

import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.aicleanphonestorage.R
import com.google.android.material.card.MaterialCardView

/** 与正式设置卡片分离，复用已提取的设置原版图标；本文件仅进入可调试的 buildType。 */
internal object DebugSettingsEntry {
    fun install(activity: AppCompatActivity, body: ViewGroup) {
        val card = MaterialCardView(activity).apply {
            radius = 12 * resources.displayMetrics.density
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        val item = SettingsItemView(activity).apply {
            id = R.id.settings_ad_debug_entry
            // 不把技术调试入口混入 Figma 五项业务菜单，release 原菜单高度保持自然测量。
            findViewById<android.widget.TextView>(R.id.settings_item_title).setText(R.string.settings_ad_debug)
            findViewById<android.widget.ImageView>(R.id.settings_item_icon).setImageResource(R.drawable.ic_settings_more)
            contentDescription = activity.getString(R.string.settings_ad_debug)
            setOnClickListener { activity.startActivity(Intent(activity, AdDebugActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        }
        card.addView(item, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(card)
    }
}
