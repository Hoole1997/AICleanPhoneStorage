package com.example.aicleanphonestorage.app.ad.renderer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.ads.renderer.GamFullScreenNativeAdRenderer
import com.example.aicleanphonestorage.R
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

class DefaultGamFullScreenNativeAdRenderer : GamFullScreenNativeAdRenderer {

    override fun createLayout(context: Context): NativeAdView {
        return LayoutInflater.from(context)
            .inflate(R.layout.layout_full_native_ad_admob, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd, lifecycleOwner: LifecycleOwner) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val mediaView = adView.findViewById<MediaView>(R.id.mv_ad_media)

        titleView?.text = nativeAd.headline.orEmpty()
        descView?.text = nativeAd.body.orEmpty()
        ctaButton?.text = nativeAd.callToAction.orEmpty()
        nativeAd.icon?.drawable?.let {
            iconView?.setImageDrawable(it)
            iconView?.visibility = View.VISIBLE
        }
        nativeAd.mediaContent?.let { mediaContent ->
            mediaView?.mediaContent = mediaContent
            mediaView?.visibility = View.VISIBLE
        } ?: run {
            mediaView?.visibility = View.GONE
        }

        adView.headlineView = titleView
        adView.bodyView = descView
        adView.callToActionView = ctaButton
        adView.iconView = iconView
        adView.starRatingView = null
        adView.advertiserView = null
        adView.priceView = null
        adView.storeView = null
        adView.registerNativeAd(nativeAd, mediaView)
    }

    override fun createLoadingView(context: Context, container: ViewGroup) {
        container.removeAllViews()
        container.addView(
            LayoutInflater.from(context).inflate(R.layout.layout_fullscreen_loading, container, false)
        )
    }
}
