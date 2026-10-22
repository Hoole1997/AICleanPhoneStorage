package com.example.aicleanphonestorage

import android.content.Intent
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.LocaleList
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.app.ad.InterstitialPlacements
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.databinding.ScreenUninstallRetentionBinding
import com.example.aicleanphonestorage.feature.retention.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.startup.StartupNavigation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RetentionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val output get() = File(context.getExternalFilesDir(null), "retention-verification").apply { mkdirs() }

    @Test fun dynamicShortcutTogglesWithoutRemovingOtherShortcutsAndUsesStartupRoute() {
        val id = "test.retention.${System.nanoTime()}"
        val manager = context.getSystemService(ShortcutManager::class.java)
        val before = manager.dynamicShortcuts.map { it.id }.filter { it != RetentionShortcut.ID }.toSet()
        val publisher = RetentionShortcut(context, id)
        try {
            publisher.sync(true, context)
            val shortcut = requireNotNull(manager.dynamicShortcuts.firstOrNull { it.id == id })
            val intent = requireNotNull(shortcut.intent)
            val incoming = StartupNavigation.read(intent)
            assertTrue(incoming.uninstallRetention)
            assertFalse(incoming.launcher)
            assertFalse(incoming.hotStart)
            assertNull(incoming.notification)
            assertTrue(StartupNavigation.read(StartupNavigation.startupIntent(context, incoming)).uninstallRetention)
            publisher.sync(false, context)
            assertFalse(manager.dynamicShortcuts.any { it.id == id })
            assertTrue(manager.dynamicShortcuts.map { it.id }.containsAll(before))
        } finally { manager.disableShortcuts(listOf(id)); manager.removeDynamicShortcuts(listOf(id)) }
    }

    @Test fun selfUninstallOnlyBuildsOwnPackageSystemIntent() {
        // 不启动/确认真实卸载；验证必须由系统接管且不能接收反馈文本或任意包名 extras。
        val intent = SelfUninstallCoordinator.intent(context.packageName)
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))
        assertEquals(setOf(Intent.EXTRA_RETURN_RESULT), intent.extras!!.keySet())
    }

    @Test fun interstitialFailureStillAdvancesOnlyAfterCallbackAndForeground() {
        org.junit.Assume.assumeTrue(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        var guard: AutoCloseable? = null
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold("retention-test") }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var ads: InterstitialActions
                lateinit var model: RetentionViewModel
                var calls = 0
                var callback: ((Boolean) -> Unit)? = null
                scenario.onActivity { activity ->
                    model = RetentionViewModel(SavedStateHandle(), EventSink { _, _ -> })
                    ads = InterstitialActions(activity) { position, result ->
                        assertEquals("uninstall_anyway", position); calls++; callback = result
                    }
                    ads.register(InterstitialPlacements.UNINSTALL_ANYWAY) { model.adFinished() }
                    assertTrue(model.beginAnyway())
                    ads.run(InterstitialPlacements.UNINSTALL_ANYWAY, InterstitialPlacements.UNINSTALL_ANYWAY)
                    assertFalse(model.beginAnyway())
                }
                assertEquals(1, calls)
                assertEquals(RetentionPage.OFFER, model.state.value.page)
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync { callback!!(false) }
                assertEquals(RetentionPage.OFFER, model.state.value.page)
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(RetentionPage.REASON, model.state.value.page) }
            }
        } finally { instrumentation.runOnMainSync { guard?.close() } }
    }

    @Test fun nativeXmlPagesFitAllLanguagesAndLargeFontWithLocalFeedback() {
        val locales = listOf("en", "zh-Hans", "de", "es", "fr", "pt", "ru", "ja", "ko", "id", "vi", "tr", "hi", "bn", "ar", "ur")
        for (tag in locales) for (scale in listOf(1f, 2f)) for (page in RetentionPage.entries) {
            instrumentation.runOnMainSync {
                val width = if (scale == 1f) 375 else 320
                val height = if (scale == 1f) 812 else 568
                val config = Configuration(context.resources.configuration).apply {
                    densityDpi = 160; fontScale = scale; screenWidthDp = width; screenHeightDp = height
                    setLocales(LocaleList(Locale.forLanguageTag(tag)))
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
                val b = ScreenUninstallRetentionBinding.inflate(LayoutInflater.from(themed))
                val renderer = RetentionRenderer(b)
                renderer.render(RetentionState(page), true, false)
                b.reason.root.setPadding(0, 44, 0, 24)
                b.offer.root.setPadding(0, 0, 0, 24)
                if (page == RetentionPage.REASON) {
                    b.reason.reasons.check(b.reason.reasonAds.id)
                    assertTrue(b.reason.reasonAds.isChecked)
                    assertFalse(b.reason.reasonDifficult.isChecked)
                    b.reason.feedback.setText("x".repeat(600))
                    assertEquals(500, b.reason.feedback.text!!.length)
                    b.reason.feedback.setText("")
                }
                fun layout() {
                    b.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    b.root.layout(0, 0, width, height)
                }
                layout(); layout() // 原版插画按宽高比调整后重新测量。
                fun check(view: View) {
                    if (view.visibility != View.VISIBLE) return
                    if (view is TextView && view.text.isNotEmpty()) {
                        val text = requireNotNull(view.layout)
                        assertTrue("$tag/$scale/$page text height: ${view.text}; layout=${text.height}, view=${view.height}, width=${view.width}", text.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                        assertEquals(view.text.length, text.getLineEnd(text.lineCount - 1))
                        for (line in 0 until text.lineCount) assertEquals(0, text.getEllipsisCount(line))
                    }
                    if (view is ViewGroup) for (i in 0 until view.childCount) check(view.getChildAt(i))
                }
                check(b.pages)
                val scroll = if (page == RetentionPage.OFFER) b.offer.root else b.reason.root
                fun save(suffix: String) {
                    if (tag !in setOf("en", "zh-Hans", "de", "ar")) return
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        b.root.draw(Canvas(bitmap))
                        File(output, "$tag-$scale-$page-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
                save("top")
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                save("bottom")
                val button = if (page == RetentionPage.OFFER) b.offer.offerKeep else b.reason.reasonKeep
                assertTrue(button.height >= 48)
            }
        }
    }
}
