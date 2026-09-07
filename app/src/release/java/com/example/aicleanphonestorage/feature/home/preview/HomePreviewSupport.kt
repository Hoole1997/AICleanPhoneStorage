package com.example.aicleanphonestorage.feature.home.preview

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.aicleanphonestorage.feature.home.ui.HomeContent

/** 正式包无预览菜单/Intent 开关/示例数值，显示 Repository 的真实或未知摘要。 */
object HomePreviewSupport {
    const val STATE_KEY = "home.design.preview"
    fun initialSelection(intent: Intent, savedState: Bundle?): String? = null
    fun content(selection: String?): HomeContent? = null
    fun attach(anchor: View, onSelection: (String) -> Unit) = Unit
}
