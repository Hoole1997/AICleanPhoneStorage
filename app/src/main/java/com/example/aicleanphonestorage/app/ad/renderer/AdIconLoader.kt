package com.example.aicleanphonestorage.app.ad.renderer

import android.widget.ImageView
import com.bumptech.glide.Glide

/** 绑定 View 生命周期并限制图标尺寸，替代来源代码中逐图创建线程和无上限 decodeStream。 */
internal object AdIconLoader {
    fun load(url: String, view: ImageView) {
        val pixels = (72 * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        Glide.with(view).load(url).override(pixels, pixels).centerInside().dontAnimate().into(view)
    }
}
