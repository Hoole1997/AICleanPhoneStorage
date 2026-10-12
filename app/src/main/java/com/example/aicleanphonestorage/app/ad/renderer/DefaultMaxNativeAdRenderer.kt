package com.example.aicleanphonestorage.app.ad.renderer

import android.content.Context
import com.android.common.bill.ads.renderer.MaxNativeAdRenderer
import com.android.common.bill.ui.max.MaxNativeAdStyle
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.example.aicleanphonestorage.R

/**
 * Migrated from Remax_Browser's BrowserMaxNativeAdRenderer.
 *
 * Keep the renderer stateless: the SDK owns ad binding and destruction, and each call creates
 * a fresh view for the current host. Do not retain its Context or a previously rendered ad view.
 */
class DefaultMaxNativeAdRenderer : MaxNativeAdRenderer {

    override fun createNativeAdView(context: Context, style: MaxNativeAdStyle): MaxNativeAdView {
        // MAX registers these asset views for binding, privacy controls and click handling.
        // Bill passes the configured slot style so standard and large layouts stay independent.
        val binder = MaxNativeAdViewBinder.Builder(style.layoutResId)
            .setTitleTextViewId(R.id.tv_ad_title)
            .setBodyTextViewId(R.id.tv_ad_description)
            .setIconImageViewId(R.id.iv_ad_icon)
            .setCallToActionButtonId(R.id.btn_ad_cta)
            .setOptionsContentViewGroupId(R.id.fl_ad_options)
            .build()

        return MaxNativeAdView(binder, context)
    }
}
