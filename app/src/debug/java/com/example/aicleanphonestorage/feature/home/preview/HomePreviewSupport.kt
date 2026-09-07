package com.example.aicleanphonestorage.feature.home.preview

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.home.ui.HeroContent
import com.example.aicleanphonestorage.feature.home.ui.HomeContent
import com.example.aicleanphonestorage.feature.home.ui.HomeStatistics
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.home.ui.HomeToolItem

/** 仅 Debug 的设计验证工具。样例永不写入 Repository，点击清理不会伪造一次扫描。 */
object HomePreviewSupport {
    const val STATE_KEY = "home.design.preview"
    const val EXTRA_MODE = "home_preview"
    private const val INITIAL = "initial"
    private const val SCANNED = "scanned"

    fun initialSelection(intent: Intent, savedState: Bundle?): String =
        (savedState?.getString(STATE_KEY) ?: intent.getStringExtra(EXTRA_MODE))
            .takeIf { it == INITIAL || it == SCANNED } ?: INITIAL

    fun content(selection: String?): HomeContent = HomeContent(
        hero = HeroContent(
            scanComplete = selection == SCANNED,
            value = if (selection == SCANNED) "199" else "40.3",
            unit = if (selection == SCANNED) "MB" else "GB",
            totalCapacity = "128GB", usedPercent = 31,
            // 稿件条长为 54/140，与标注的 31% 不一致；仅预览按原图，真实数据使用实际占比。
            progressFraction = 54f / 140f,
        ),
        statistics = HomeStatistics(download = "637B/S", available = "87.7GB", used = "31%"),
        tools = HomeTool.entries.map { HomeToolItem(it, "12.5GB") },
    )

    fun attach(anchor: View, onSelection: (String) -> Unit) {
        ViewCompat.setStateDescription(anchor, anchor.context.getString(R.string.home_preview_hint))
        anchor.setOnLongClickListener {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(0, 0, 0, R.string.home_preview_initial)
                menu.add(0, 1, 1, R.string.home_preview_scanned)
                setOnMenuItemClickListener { item ->
                    onSelection(if (item.itemId == 1) SCANNED else INITIAL)
                    true
                }
            }.show()
            true
        }
    }
}
