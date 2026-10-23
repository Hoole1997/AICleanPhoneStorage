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

    @Test fun reasonUninstallOnlyBuildsOwnPackageAppInfoIntent() {
        // 只导航本应用的系统信息页，不构建删除请求，也不将反馈参数传给系统。
        val intent = RetentionSettingsCoordinator.intent(context.packageName)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
    }

    @Test fun reasonUninstallIntentActuallyOpensAndroidAppInfo() {
        var guard: AutoCloseable? = null
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold("retention-settings-test") }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                val automation = instrumentation.uiAutomation
                val event = automation.executeAndWaitForEvent({
                    scenario.onActivity { it.startActivity(RetentionSettingsCoordinator.intent(it.packageName)) }
                }, { it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    it.packageName?.toString() == "com.android.settings" }, 10_000)
                event.recycle()
                val label = context.applicationInfo.loadLabel(context.packageManager).toString()
                fun containsLabel(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
                    return (0 until node.childCount).any { containsLabel(node.getChild(it)) }
                }
                // Android 37 的系统设置使用 Compose 虚拟节点；窗口事件早于内容加载，按可见内容有界等待。
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var root = automation.rootInActiveWindow
                while (!containsLabel(root) && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                    root = automation.rootInActiveWindow
                }
                assertEquals("com.android.settings", root?.packageName?.toString())
                assertTrue("系统应用信息页应显示当前 App 名称", containsLabel(root))
                val screenshot = requireNotNull(automation.takeScreenshot())
                try { File(output, "system-app-info.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                } } finally { screenshot.recycle() }
                // 不点击系统卸载、停用或清数据，只验证正常返回。
                assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                instrumentation.waitForIdleSync()
            }
        } finally { instrumentation.runOnMainSync { guard?.close() } }
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

    @Test fun reasonPressFeedbackIsClippedToTheTouchedRow() {
        var guard: AutoCloseable? = null
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold("retention-press-test") }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var binding: ScreenUninstallRetentionBinding
                scenario.onActivity { activity ->
                    binding = ScreenUninstallRetentionBinding.inflate(activity.layoutInflater)
                    val renderer = RetentionRenderer(binding)
                    activity.setContentView(binding.root)
                    binding.reason.root.setPadding(0, 80, 0, 32)
                    renderer.render(RetentionState(RetentionPage.REASON), true, false)
                }
                instrumentation.waitForIdleSync()
                val point = FloatArray(2)
                scenario.onActivity {
                    val row = binding.reason.reasonAds
                    val location = IntArray(2)
                    row.getLocationOnScreen(location)
                    point[0] = location[0] + row.width - row.compoundDrawablesRelative[2].intrinsicWidth / 2f
                    point[1] = location[1] + row.height / 2f
                }
                val started = SystemClock.uptimeMillis()
                fun touch(action: Int) {
                    val event = android.view.MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                touch(android.view.MotionEvent.ACTION_DOWN)
                // 捕获真实按压中的 Ripple，而非软件 Canvas 的静态背景模拟。
                SystemClock.sleep(180)
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try { File(output, "reason-option-pressed.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                } } finally { screenshot.recycle() }
                touch(android.view.MotionEvent.ACTION_UP)
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val row = binding.reason.reasonAds
                    assertTrue(row.isChecked)
                    assertFalse(binding.reason.reasonDifficult.isChecked)
                    for (choice in listOf(binding.reason.reasonDifficult, binding.reason.reasonAds, binding.reason.reasonResults,
                        binding.reason.reasonFeatures, binding.reason.reasonSlow, binding.reason.reasonOther)) {
                        val ripple = choice.background as android.graphics.drawable.RippleDrawable
                        assertNotNull("选项波纹必须有显式遮罩，不能使用系统无边界按钮波纹", ripple.findDrawableByLayerId(android.R.id.mask))
                        assertEquals(choice.width, ripple.bounds.width())
                        assertEquals(choice.height, ripple.bounds.height())
                    }
                }
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
