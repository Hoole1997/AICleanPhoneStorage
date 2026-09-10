package com.example.aicleanphonestorage.app.ad

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ui.NativeAdStyleType
import kotlinx.coroutines.launch
import net.corekit.core.controller.AdSlotSwitchController

fun FragmentActivity.loadNative(
    positionName: String,
    container: ViewGroup,
    styleType: NativeAdStyleType = NativeAdStyleType.STANDARD,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit = {}
) {
    lifecycleScope.launch {
        try {
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                container.visibility = View.GONE
                call.invoke(false)
                return@launch
            }

            val success = AdShowExt.showNativeAdInContainer(
                context = container.context,
                container = container,
                styleType = styleType
            )

            if (success) {
                container.visibility = View.VISIBLE
                call.invoke(true)
            } else {
                container.visibility = View.GONE
                call.invoke(false)
            }
        } catch (_: Exception) {
            container.visibility = View.GONE
            call.invoke(false)
        }
    }
}

fun FragmentActivity.loadInterstitial(
    positionName: String,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit
) {
    lifecycleScope.launch {
        try {
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                call.invoke(false)
                return@launch
            }

            when (AdShowExt.showInterstitialAd(this@loadInterstitial)) {
                is AdResult.Success -> call.invoke(true)
                is AdResult.Failure -> call.invoke(false)
            }
        } catch (_: Exception) {
            call.invoke(false)
        }
    }
}

fun FragmentActivity.loadSplash(
    positionName: String,
    condition: () -> Boolean = { true },
    call: (Boolean) -> Unit
) {
    lifecycleScope.launch {
        try {
            if (!condition.invoke() || !isAdSlotEnabled(positionName)) {
                call.invoke(false)
                return@launch
            }

            when (AdShowExt.showAppOpenAd(this@loadSplash)) {
                is AdResult.Success -> call.invoke(true)
                is AdResult.Failure -> call.invoke(false)
            }
        } catch (_: Exception) {
            call.invoke(false)
        }
    }
}

private fun isAdSlotEnabled(positionName: String): Boolean {
    // TODO: 临时返回 true
    return true
    return AdSlotSwitchController.isEnabled(positionName)
}
