package com.example.aicleanphonestorage.app.ad.renderer

import android.view.View
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.app.ad.LoadingAdRequest
import com.example.aicleanphonestorage.app.ad.loadingAdRequest

class DefaultAdLoadingDialogRenderer : AdLoadingDialogRenderer {

    override fun getLayoutResId(): Int = R.layout.layout_ad_loading

    override fun onViewCreated(view: View, onReady: () -> Unit) {
        // SDK 的默认关闭仅 hide()；托管请求明确取消等待并立即续接业务。
        // 绑定快照而非点击时查询，防止旧窗口关闭误伤新请求。
        val request = loadingAdRequest(view.context)
        view.setTag(R.id.ad_loading_request, request)
        if (request != null) view.findViewById<View>(R.id.ad_loading_close)?.setOnClickListener {
            request.cancelLoading()
        }
        onReady()
    }

    override fun updateText(view: View, text: String) {
        view.findViewById<TextView>(R.id.tv_loading_text)?.text = text
    }

    // 托管请求已绑定关闭行为，返回 null 防止 SDK 覆盖；其他广告入口保持 SDK 原行为。
    override fun findCloseView(view: View): View? =
        if (view.getTag(R.id.ad_loading_request) is LoadingAdRequest) null
        else view.findViewById(R.id.ad_loading_close)

    override fun onDestroy(view: View) {
        view.findViewById<View>(R.id.ad_loading_close)?.setOnClickListener(null)
        view.setTag(R.id.ad_loading_request, null)
        view.findViewById<AdLoadingAnimationView>(R.id.ad_loading_progress)?.release()
    }
}
