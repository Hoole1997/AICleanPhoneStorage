package com.example.aicleanphonestorage.app.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ui.dialog.ADLoadingDialog
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * 请求只挂在所属 Activity 窗口上，不让全局 SDK renderer 保存 Activity/Job。
 * renderer 在创建时绑定该次请求的快照，旧弹框的关闭事件不会取消后一次请求。
 */
internal fun loadingAdRequest(context: Context): LoadingAdRequest? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current.window.decorView.getTag(R.id.ad_loading_request) as? LoadingAdRequest
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return null
}

/** 与广告实现分离，可用真实 SDK 加载框和可控挂起请求验证关闭/生命周期，不请求真实广告。 */
internal fun FragmentActivity.launchInterstitialRequest(
    position: String,
    call: (Boolean) -> Unit,
    show: suspend () -> Boolean,
): Job = lifecycleScope.launch {
    val decor: View = window.decorView
    if (decor.getTag(R.id.ad_loading_request) != null) {
        call(false)
        return@launch
    }
    val transition = ForegroundTransitionGuard.hold("interstitial:$position")
    val work = coroutineContext[Job]!!
    lateinit var request: LoadingAdRequest
    request = LoadingAdRequest(
        cancelWork = { work.cancel(CancellationException("Ad loading closed by user")) },
        hideLoading = { ADLoadingDialog.hide() },
        release = {
            if (decor.getTag(R.id.ad_loading_request) === request) decor.setTag(R.id.ad_loading_request, null)
            transition.close()
        },
        onResult = call,
    )
    decor.setTag(R.id.ad_loading_request, request)
    var success = false
    try {
        success = show()
        coroutineContext.ensureActive()
    } catch (cancelled: CancellationException) {
        success = false
        throw cancelled
    } catch (_: Exception) {
        success = false
    } finally {
        // 业务回调放在 try/catch 外，业务抛错不会被误当作广告失败而再次调用。
        request.complete(success)
    }
}
