package com.example.aicleanphonestorage.feature.settings.addebug

import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ui.NativeAdStyleType
import com.example.aicleanphonestorage.app.risk.DeviceRiskRuntime
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 展示只在独立的调试预览 Activity 中执行，本类不保存 Activity、View 或广告实例。
 *
 * 嵌入式原生/Banner 的宿主必须在清除或更换预览时 finish：当前发布 SDK 的
 * AdDestroyManager 未作为稳定公开 API 保留，SDK 会通过该 Activity 的 onDestroy
 * 释放展示资源。不能反射混淆类，也不能为了关闭预览销毁全局预加载缓存。
 */
internal class SdkDebugAdPresenter {
    suspend fun show(
        activity: FragmentActivity,
        container: ViewGroup,
        format: DebugAdFormat,
    ): String = withContext(Dispatchers.Main.immediate) {
        // 设置页来自正常启动路径，沿用启动页已执行的 consent 顺序和 allowed 处理方式。
        // 不能在调试页再弹一次同意框，也不能跳过真实设备风控的首次有期限决策。
        DeviceRiskRuntime.awaitInitialDecision()
        currentCoroutineContext().ensureActive()
        if (!activity.canRequestNow()) return@withContext "Preview cancelled: activity is not in the foreground."

        val transition = ForegroundTransitionGuard.hold(PREVIEW_POSITION)
        try {
            container.visibility = View.VISIBLE
            when (format) {
                DebugAdFormat.APP_OPEN -> describe(
                    AdShowExt.showAppOpenAd(
                        activity = activity,
                        countdown = null,
                        competeWithInterstitial = false,
                        position = PREVIEW_POSITION,
                    ),
                )
                DebugAdFormat.INTERSTITIAL -> describe(
                    AdShowExt.showInterstitialAd(
                        activity = activity,
                        ignoreFullNative = true,
                        countdown = null,
                        position = PREVIEW_POSITION,
                    ),
                )
                DebugAdFormat.REWARDED -> {
                    var rewardEarned = false
                    val result = AdShowExt.showRewardedAd(
                        activity = activity,
                        onRewardEarned = { rewardEarned = true },
                        countdown = null,
                        competeWithInterstitial = false,
                        position = PREVIEW_POSITION,
                    )
                    "${describe(result)} Reward earned=$rewardEarned."
                }
                DebugAdFormat.FULL_SCREEN_NATIVE -> describe(
                    AdShowExt.showFullScreenNativeAdInContainer(
                        activity = activity,
                        showInterstitial = false,
                        position = PREVIEW_POSITION,
                    ),
                )
                DebugAdFormat.NATIVE -> {
                    val shown = AdShowExt.showNativeAdInContainer(
                        // 传真实 FragmentActivity，确保 SDK 能绑定宿主销毁回调。
                        context = activity,
                        container = container,
                        styleType = NativeAdStyleType.STANDARD,
                        position = PREVIEW_POSITION,
                    )
                    if (shown) "SDK native result=true; preview bound to the container."
                    else "SDK native result=false; no native preview was bound."
                }
                DebugAdFormat.BANNER -> describe(
                    AdShowExt.showBannerAd(
                        activity = activity,
                        container = container,
                        position = PREVIEW_POSITION,
                    ),
                )
            }.also {
                // 第三方 SDK 可能捕获取消并返回普通结果；已销毁页面不能因此发布迟到成功。
                currentCoroutineContext().ensureActive()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // 不显示 SDK 原始异常：其中可能附带广告位、请求 URL 或运行时凭据。
            "Preview failed: SDK call threw an exception."
        } catch (_: LinkageError) {
            currentCoroutineContext().ensureActive()
            "Preview failed: SDK runtime API is unavailable."
        } finally {
            transition.close()
        }
    }

    /**
     * 仅在独立预览宿主退出时清空 View；SDK 在同一 Activity 的 onDestroy 清理广告。
     * activity 参数保留为宿主生命周期契约，不能在普通设置页复用此方法更换活跃广告。
     */
    @MainThread
    @Suppress("UNUSED_PARAMETER")
    fun clear(activity: FragmentActivity, container: ViewGroup) {
        container.removeAllViews()
    }

    private fun describe(result: AdResult<*>): String = when (result) {
        is AdResult.Success -> "SDK result=Success; request completed."
        // 代码保持 SDK 原值；不把任何失败或未知结果显示成成功，也不伪造填充。
        is AdResult.Failure -> "SDK result=Failure; code=${result.error.code}."
    }

    private fun FragmentActivity.canRequestNow(): Boolean =
        !isFinishing && !isDestroyed && hasWindowFocus() &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private companion object {
        const val PREVIEW_POSITION = "debug_ad_preview"
    }
}
