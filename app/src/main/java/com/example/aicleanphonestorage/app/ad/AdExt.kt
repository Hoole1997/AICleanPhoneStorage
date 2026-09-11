package com.example.aicleanphonestorage.app.ad

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ui.NativeAdStyleType
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.corekit.core.controller.AdSlotSwitchController

// Slot Key 同时传给远程开关和 SDK position，保证展示统计保留真实业务来源。
fun FragmentActivity.loadNative(
    positionName: String,
    container: ViewGroup,
    styleType: NativeAdStyleType = NativeAdStyleType.STANDARD,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit = {},
) =
    lifecycleScope.launch {
        try {
            android.util.Log.d("CleanAds", "Native request: slot=$positionName")
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                container.visibility = View.GONE
                call.invoke(false)
                return@launch
            }

            val success =
                AdShowExt.showNativeAdInContainer(
                    context = container.context,
                    container = container,
                    styleType = styleType,
                    position = positionName,
                )

            coroutineContext.ensureActive()
            android.util.Log.d("CleanAds", "Native result: slot=$positionName, success=$success")
            if (success) {
                container.visibility = View.VISIBLE
                call.invoke(true)
            } else {
                container.visibility = View.GONE
                call.invoke(false)
            }
        } catch (cancelled: CancellationException) {
            // 生命周期取消不作为广告失败回调，避免后台旧请求覆盖新页面状态。
            throw cancelled
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            container.visibility = View.GONE
            call.invoke(false)
        }
    }

fun FragmentActivity.loadInterstitial(
    positionName: String,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit,
) {
    lifecycleScope.launch {
        val transition = ForegroundTransitionGuard.hold("interstitial:$positionName")
        try {
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                call.invoke(false)
                return@launch
            }

            when (AdShowExt.showInterstitialAd(this@loadInterstitial, position = positionName)) {
                is AdResult.Success -> call.invoke(true)
                is AdResult.Failure -> call.invoke(false)
            }
        } catch (_: Exception) {
            call.invoke(false)
        } finally {
            transition.close()
        }
    }
}

fun FragmentActivity.loadSplash(
    positionName: String,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit,
) {
    lifecycleScope.launch {
        val transition = ForegroundTransitionGuard.hold("splash:$positionName")
        try {
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                call.invoke(false)
                return@launch
            }

            when (AdShowExt.showAppOpenAd(this@loadSplash, position = positionName)) {
                is AdResult.Success -> call.invoke(true)
                is AdResult.Failure -> call.invoke(false)
            }
        } catch (_: Exception) {
            call.invoke(false)
        } finally {
            transition.close()
        }
    }
}

internal fun isAdSlotEnabled(positionName: String): Boolean {
    // 测试阶段刻意放行所有广告位；线上参数尚未配置。Slot Key 清单见 docs/ads/slots.md。
    //todo 联调完成后移除这一行 return true，恢复下面的远程开关判断。
    return true
    return AdSlotSwitchController.isEnabled(positionName)
}
