package com.example.aicleanphonestorage

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.common.bill.ui.max.MaxNativeAdStyle
import com.android.common.bill.ui.view.AdLabelView
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.example.aicleanphonestorage.app.ad.renderer.DefaultMaxFullScreenNativeAdRenderer
import com.example.aicleanphonestorage.app.ad.renderer.DefaultMaxNativeAdRenderer
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrated MAX layouts use the real SDK view/binder, with isolated text and drawable fixtures.
 * No SDK initialization, ad request, render(ad), impression or click is invoked by these tests.
 * Measured SDK-view render fixtures are saved to externalFilesDir/max-native-tests for visual verification.
 * These PNGs isolate the native view; they are not live ads or evidence of an onscreen ad impression.
 */
@RunWith(AndroidJUnit4::class)
class MaxNativeRenderingDeviceTest {
    @get:Rule val noHotStartAds = NoHotStartAdsRule()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun slotStyleSelectsStandardAndLargeLayoutsWithSdkAssetBinding() {
        val standard = captureNative("max_standard_320_font1", R.layout.layout_native_ad_max, 1f)
        val large = captureNative("max_large_320_font1", R.layout.layout_native_ad_max_large, 1f)
        // An ignored style would silently render the compact card for both slot sizes.
        assertTrue("Large slot must retain the large card", large.height > standard.height)
        assertTrue("Large card CTA must span its content column", large.ctaWidth > standard.ctaWidth)
    }

    @Test
    fun compactAndLargeCardsKeepCtaAndTextHeightAtDoubleFontScale() {
        captureNative("max_standard_320_font2", R.layout.layout_native_ad_max, 2f)
        captureNative("max_large_320_font2", R.layout.layout_native_ad_max_large, 2f)
    }

    @Test
    fun fullScreenRendererBindsMediaAndOptionsAndKeepsAssetsInsideItsHost() {
        for (fontScale in listOf(1f, 2f)) {
            capture("max_fullscreen_320_font${fontScale.toInt()}", fontScale, fullScreen = true) { context ->
                DefaultMaxFullScreenNativeAdRenderer().createNativeAdView(context)
            }
        }
        // A fixed 600dp content area checks small portrait hosts where enlarged header text leaves less media space.
        capture("max_fullscreen_320_height600_font2", 2f, fullScreen = true, heightDp = 600) { context ->
            DefaultMaxFullScreenNativeAdRenderer().createNativeAdView(context)
        }
    }

    private fun captureNative(name: String, layoutResId: Int, fontScale: Float) =
        capture(name, fontScale, fullScreen = false) { context ->
            DefaultMaxNativeAdRenderer().createNativeAdView(context, MaxNativeAdStyle(layoutResId))
        }

    private fun capture(
        name: String,
        fontScale: Float,
        fullScreen: Boolean,
        heightDp: Int? = null,
        build: (Context) -> MaxNativeAdView,
    ): Geometry {
        lateinit var adView: MaxNativeAdView
        var geometry: Geometry? = null
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val configuration = Configuration(activity.resources.configuration).apply {
                    screenWidthDp = 320
                    if (heightDp != null) screenHeightDp = heightDp
                    this.fontScale = fontScale
                    setLocale(Locale.US)
                }
                val themed = ContextThemeWrapper(
                    activity.createConfigurationContext(configuration),
                    R.style.Theme_AICleanPhoneStorage,
                )
                adView = build(themed)
                assertSdkAssets(adView, fullScreen)
                // Runtime XML must stay empty until an actual ad is bound; fixtures never enter production.
                assertTrue("$name title placeholder", adView.titleTextView.text.isEmpty())
                assertTrue("$name body placeholder", adView.bodyTextView.text.isEmpty())
                assertTrue("$name CTA placeholder", adView.callToActionButton.text.isEmpty())
                fillFixture(adView, fullScreen)

                val host = FrameLayout(activity).apply { setBackgroundColor(Color.LTGRAY) }
                host.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        dp(adView, 320),
                        heightDp?.let { dp(adView, it) }
                            ?: if (fullScreen) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ),
                )
                // AboutActivity is edge-to-edge: apply safe insets once on the test host, as an SDK host would.
                ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
                    val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                    insets
                }
                activity.setContentView(host)
                ViewCompat.requestApplyInsets(host)
            }
            instrumentation.waitForIdleSync()
            // Preserve native-view pixels on failure too, without capturing the unrelated About host window.
            saveFixture(name, adView)
            scenario.onActivity {
                Log.i("MaxRenderingTest", "$name\n${describeTree(adView)}")
                assertEquals("$name fixture width", dp(adView, 320), adView.width)
                if (heightDp != null) assertEquals("$name fixture height", dp(adView, heightDp), adView.height)
                assertTrue("$name must be attached and visible", adView.isShown && adView.isAttachedToWindow)
                for (text in listOf(adView.titleTextView, adView.bodyTextView, adView.callToActionButton)) {
                    assertTextHeight(name, text)
                    assertInside(name, adView, text)
                }
                val cta = adView.callToActionButton
                assertTrue("$name CTA touch height", cta.height >= dp(adView, 48))
                val ctaLayout = requireNotNull(cta.layout)
                assertEquals("$name CTA must not ellipsize", 0, (0 until ctaLayout.lineCount).sumOf { ctaLayout.getEllipsisCount(it) })
                assertInside(name, adView, adView.optionsContentViewGroup)
                val optionsBounds = bounds(adView, adView.optionsContentViewGroup)
                // Privacy controls remain visible and independently touchable, including compact elevated buttons.
                assertFalse("$name privacy options overlap CTA", Rect.intersects(optionsBounds, bounds(adView, cta)))
                if (fullScreen) {
                    val label = requireNotNull(adView.findViewById<AdLabelView>(R.id.tv_ad_label))
                    assertTrue("$name AD label must be visible", label.isShown && label.isAttachedToWindow)
                    assertTrue("$name AD label must have measurable pixels", label.width > 0 && label.height > 0)
                    assertTrue("$name AD label must be accessible", !label.contentDescription.isNullOrBlank())
                    assertFalse("$name AD label is attribution, not a click asset", label.isClickable)
                    assertFalse("$name AD label must not be registered as a MAX click asset", adView.clickableViews.contains(label))
                    assertInside(name, adView, label)
                    val labelBounds = bounds(adView, label)
                    for (asset in listOf(adView.iconImageView, adView.titleTextView, adView.bodyTextView,
                        adView.callToActionButton, adView.optionsContentViewGroup, adView.mediaContentViewGroup)) {
                        assertFalse("$name AD label overlaps asset ${asset.id}", Rect.intersects(labelBounds, bounds(adView, asset)))
                    }
                    assertFalse("$name privacy options overlap title", Rect.intersects(optionsBounds, bounds(adView, adView.titleTextView)))
                    val media = requireNotNull(adView.mediaContentViewGroup)
                    assertTrue("$name media must occupy visible space", media.width > 0 && media.height > 0)
                    assertInside(name, adView, media)
                    val mediaBounds = bounds(adView, media)
                    assertTrue("$name media must not cover body", mediaBounds.top >= bounds(adView, adView.bodyTextView).bottom)
                    assertTrue("$name media must not cover CTA", mediaBounds.bottom <= bounds(adView, cta).top)
                }
                geometry = Geometry(adView.height, cta.width)
            }
        }
        return requireNotNull(geometry)
    }

    private fun assertSdkAssets(view: MaxNativeAdView, fullScreen: Boolean) {
        assertSame(view.findViewById<TextView>(R.id.tv_ad_title), view.titleTextView)
        assertSame(view.findViewById<TextView>(R.id.tv_ad_description), view.bodyTextView)
        assertSame(view.findViewById<View>(R.id.iv_ad_icon), view.iconImageView)
        assertSame(view.findViewById<View>(R.id.btn_ad_cta), view.callToActionButton)
        assertSame(view.findViewById<ViewGroup>(R.id.fl_ad_options), view.optionsContentViewGroup)
        assertNotNull("Privacy options must be bindable", view.optionsContentViewGroup)
        if (fullScreen) {
            assertTrue("Full-screen attribution must use the SDK AdLabelView", view.findViewById<View>(R.id.tv_ad_label) is AdLabelView)
            assertSame(view.findViewById<ViewGroup>(R.id.fl_media_container), view.mediaContentViewGroup)
            assertNotNull("Full-screen media must be bindable", view.mediaContentViewGroup)
        } else {
            assertNull("Compact cards have no media asset", view.mediaContentViewGroup)
        }
    }

    private fun fillFixture(view: MaxNativeAdView, fullScreen: Boolean) {
        view.titleTextView.text = "Test placement title"
        view.bodyTextView.text = "Test-only description used to check native asset sizing."
        view.callToActionButton.text = "Install"
        view.iconImageView.setImageDrawable(ColorDrawable(Color.DKGRAY))
        // Use a plain sized child to exercise the options/media hosts without MAX click or impression wiring.
        view.optionsContentViewGroup.addView(
            View(view.context).apply { setBackgroundColor(Color.CYAN) },
            ViewGroup.LayoutParams(dp(view, 20), dp(view, 20)),
        )
        if (fullScreen) {
            view.mediaContentViewGroup.addView(
                TextView(view.context).apply {
                    text = "Test-only media fixture"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.DKGRAY)
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun assertTextHeight(name: String, view: TextView) {
        val layout = requireNotNull(view.layout)
        assertTrue("$name text must have measured lines: ${view.text}", layout.lineCount > 0)
        assertTrue(
            "$name text vertically clipped: ${view.text}",
            view.height - view.compoundPaddingTop - view.compoundPaddingBottom >= layout.height,
        )
    }

    private fun assertInside(name: String, root: ViewGroup, view: View) {
        val rect = bounds(root, view)
        assertTrue("$name asset outside host: ${describe(view)} $rect root=${root.width}x${root.height}", rect.left >= 0 && rect.top >= 0 && rect.right <= root.width && rect.bottom <= root.height)
    }

    private fun bounds(root: ViewGroup, view: View) =
        Rect().also {
            // A singleLine TextView may scroll its internal very-wide text layout by ~2^19 pixels.
            // getDrawingRect includes that own scroll before ancestor conversion, yielding the physical view bounds.
            view.getDrawingRect(it)
            root.offsetDescendantRectToMyCoords(view, it)
        }

    private fun describeTree(view: View, depth: Int = 0): String = buildString {
        append("  ".repeat(depth)).append(describe(view)).append('\n')
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            append(describeTree(view.getChildAt(index), depth + 1))
        }
    }

    private fun describe(view: View): String {
        val id = if (view.id == View.NO_ID) "no-id" else runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault(view.id.toString())
        val params = view.layoutParams
        val margins = (params as? ViewGroup.MarginLayoutParams)?.let { " margins=${it.leftMargin},${it.topMargin},${it.rightMargin},${it.bottomMargin}" }.orEmpty()
        return "${view.javaClass.simpleName}($id) position=${view.left},${view.top},${view.right},${view.bottom} measured=${view.measuredWidth}x${view.measuredHeight} scroll=${view.scrollX},${view.scrollY} lp=${params?.width}x${params?.height}$margins"
    }

    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).roundToInt()

    private fun saveFixture(name: String, view: View) {
        var bitmap: Bitmap? = null
        try {
            instrumentation.runOnMainSync {
                assertTrue("$name fixture must have measured dimensions", view.width > 0 && view.height > 0)
                // Only the measured native view is rendered; the fixture never registers a MAX ad or tracker.
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(requireNotNull(bitmap)))
            }
            val rendered = requireNotNull(bitmap)
            val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "max-native-tests").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { output ->
                assertTrue("$name fixture encoding", rendered.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            // Keep only one native-view bitmap at a time, including assertion and file-write failure paths.
            bitmap?.recycle()
        }
    }

    private data class Geometry(val height: Int, val ctaWidth: Int)
}
