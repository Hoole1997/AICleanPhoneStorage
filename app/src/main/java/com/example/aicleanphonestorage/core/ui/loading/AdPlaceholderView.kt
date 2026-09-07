package com.example.aicleanphonestorage.core.ui.loading

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.example.aicleanphonestorage.R

/** 纯静态广告槽：没有 SDK/网络/点击行为，之后可替换内容，不影响业务任务或弹窗取消。 */
class AdPlaceholderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init { LayoutInflater.from(context).inflate(R.layout.view_ad_placeholder, this, true) }
}
