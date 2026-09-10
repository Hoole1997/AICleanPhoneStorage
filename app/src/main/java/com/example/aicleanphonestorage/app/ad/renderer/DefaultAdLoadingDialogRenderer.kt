package com.example.aicleanphonestorage.app.ad.renderer

import android.view.View
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import com.example.aicleanphonestorage.R

class DefaultAdLoadingDialogRenderer : AdLoadingDialogRenderer {

    override fun getLayoutResId(): Int = R.layout.layout_ad_loading

    override fun onViewCreated(view: View, onReady: () -> Unit) {
        onReady()
    }

    override fun updateText(view: View, text: String) {
        view.findViewById<TextView>(R.id.tv_loading_text)?.text = text
    }

    // 只提供关闭入口，由 SDK 绑定点击事件并结束等待，保证业务 call 回调按 SDK 流程执行。
    override fun findCloseView(view: View): View? = view.findViewById(R.id.ad_loading_close)

    override fun onDestroy(view: View) {
        view.findViewById<AdLoadingAnimationView>(R.id.ad_loading_progress)?.release()
    }
}
