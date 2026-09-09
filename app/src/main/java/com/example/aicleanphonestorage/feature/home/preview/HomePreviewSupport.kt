package com.example.aicleanphonestorage.feature.home.preview

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.BuildConfig
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.feature.home.ui.HeroContent
import com.example.aicleanphonestorage.feature.home.ui.HomeContent
import com.example.aicleanphonestorage.feature.home.ui.HomeStatistics
import com.example.aicleanphonestorage.feature.home.ui.HomeTool
import com.example.aicleanphonestorage.feature.home.ui.HomeToolItem

/**
 * 由构建生成的 BuildConfig.DEBUG 开启预览；渠道目录不承担调试/正式代码分支。
 * 每个入口都检查构建标志，Release 不读取预览 Intent/状态、不提供样例数据或安装菜单。
 * 样例永不写入 Repository，点击清理不会伪造一次扫描；Release 的常量分支可由 R8 消除。
 */
object HomePreviewSupport {
    const val STATE_KEY = "home.design.preview"
    const val EXTRA_MODE = "home_preview"
    private const val EXPLICIT = "home.design.preview.explicit"
    private const val INITIAL = "initial"
    private const val SCANNED = "scanned"

    fun initialSelection(intent: Intent, savedState: Bundle?): String? =
        if (!BuildConfig.DEBUG) null else
        ((if (savedState?.getBoolean(EXPLICIT) == true) savedState.getString(STATE_KEY) else null)
                ?: intent.getStringExtra(EXTRA_MODE))
            .takeIf { it == INITIAL || it == SCANNED }

    fun saveSelection(state: Bundle, selection: String?) {
        if (!BuildConfig.DEBUG) return
        state.putBoolean(EXPLICIT, selection == INITIAL || selection == SCANNED)
        state.putString(STATE_KEY, selection)
    }

    fun content(selection: String?): HomeContent? =
        if (!BuildConfig.DEBUG || (selection != INITIAL && selection != SCANNED)) null
        else
            HomeContent(
                hero =
                    HeroContent(
                        scanComplete = selection == SCANNED,
                        value = if (selection == SCANNED) "199" else "40.3",
                        unit = if (selection == SCANNED) "MB" else "GB",
                        totalCapacity = "128GB",
                        usedPercent = 31,
                        // 稿件条长为 54/140，与标注的 31% 不一致；仅预览按原图，真实数据使用实际占比。
                        progressFraction = 54f / 140f,
                    ),
                statistics =
                    HomeStatistics(download = "637B/S", available = "87.7GB", used = "31%"),
                tools = HomeTool.entries.map { HomeToolItem(it, "12.5GB") },
            )

    fun attach(anchor: View, onSelection: (String?) -> Unit) {
        if (!BuildConfig.DEBUG) return
        ViewCompat.setStateDescription(anchor, anchor.context.getString(R.string.home_preview_hint))
        anchor.setOnLongClickListener {
            PopupMenu(anchor.context, anchor)
                .apply {
                    menu.add(0, 2, 0, R.string.home_preview_live)
                    menu.add(0, 0, 1, R.string.home_preview_initial)
                    menu.add(0, 1, 2, R.string.home_preview_scanned)
                    setOnMenuItemClickListener { item ->
                        onSelection(
                            when (item.itemId) {
                                0 -> INITIAL
                                1 -> SCANNED
                                else -> null
                            }
                        )
                        true
                    }
                }
                .show()
            true
        }
    }
}
