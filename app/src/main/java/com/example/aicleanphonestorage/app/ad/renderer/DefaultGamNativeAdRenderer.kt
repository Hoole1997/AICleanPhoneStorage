package com.example.aicleanphonestorage.app.ad.renderer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.GamNativeAdRenderer
import com.android.common.bill.ui.NativeAdStyle
import com.example.aicleanphonestorage.R
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

class DefaultGamNativeAdRenderer(
    private val fallbackLayoutResId: Int = R.layout.layout_native_ad_admob
) : GamNativeAdRenderer {

    override fun createLayout(context: Context, style: NativeAdStyle): NativeAdView {
        val layoutResId = style.layoutResId.takeIf { it != 0 } ?: fallbackLayoutResId
        return LayoutInflater.from(context).inflate(layoutResId, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)

        titleView?.text = nativeAd.headline.orEmpty()
        ctaButton?.text = nativeAd.callToAction.orEmpty()
        descView?.text = nativeAd.body.orEmpty()
        nativeAd.icon?.drawable?.let {
            iconView?.setImageDrawable(it)
            iconView?.visibility = View.VISIBLE
        }

        adView.headlineView = titleView
        adView.callToActionView = ctaButton
        adView.iconView = iconView
        adView.bodyView = descView
        adView.advertiserView = null
        adView.priceView = null
        adView.storeView = null
        adView.registerNativeAd(nativeAd, null)
    }
}
