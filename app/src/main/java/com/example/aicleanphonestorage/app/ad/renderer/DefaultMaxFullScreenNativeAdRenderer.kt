package com.example.aicleanphonestorage.app.ad.renderer

import android.content.Context
import com.android.common.bill.ads.renderer.MaxFullScreenNativeAdRenderer
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.example.aicleanphonestorage.R

/**
 * Migrated from Remax_Browser's BrowserMaxFullScreenNativeAdRenderer.
 *
 * The full-screen host and SDK own the ad lifecycle; retain neither their Context nor ad views.
 */
class DefaultMaxFullScreenNativeAdRenderer : MaxFullScreenNativeAdRenderer {

    override fun createNativeAdView(context: Context): MaxNativeAdView {
        // Media and privacy views must stay SDK-managed so playback and ad controls work normally.
        val binder = MaxNativeAdViewBinder.Builder(R.layout.layout_full_native_ad_max)
            .setTitleTextViewId(R.id.tv_ad_title)
            .setBodyTextViewId(R.id.tv_ad_description)
            .setIconImageViewId(R.id.iv_ad_icon)
            .setMediaContentViewGroupId(R.id.fl_media_container)
            .setCallToActionButtonId(R.id.btn_ad_cta)
            .setOptionsContentViewGroupId(R.id.fl_ad_options)
            .build()

        return MaxNativeAdView(binder, context)
    }
}
