package com.example.aicleanphonestorage

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.android.common.bill.ads.bidding.AdSourceController
import com.android.common.bill.ads.bidding.AdSourceController.AdSource
import com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor
import com.example.aicleanphonestorage.databinding.ScreenSettingsShellBinding
import com.example.aicleanphonestorage.databinding.ViewAdDebugBinding
import com.example.aicleanphonestorage.databinding.ViewAdDebugPreviewBinding
import com.example.aicleanphonestorage.feature.settings.AdDebugActivity
import com.example.aicleanphonestorage.feature.settings.AdDebugPreviewActivity
import com.example.aicleanphonestorage.feature.settings.SettingsActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 只控制 DEBUG 设置及离屏排版，不点击广告请求按钮，也不请求或展示真实广告。
 * SDK 同步 getter/setter 内部访问 DataStore，因此一律放在 IO；每个配置测试 finally 恢复原值。
 */
@RunWith(AndroidJUnit4::class)
class AdDebugDeviceTest {
    @get:Rule val noHotStartAds = NoHotStartAdsRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun settingsEntryOpensToolsAndPreservesExistingSdkControlsWithoutShowingAnAd() = preservingSdkControls {
        val configured = Controls(AdSource.MAX, false)
        writeControls(configured)
        ActivityScenario.launch(SettingsActivity::class.java).use { settings ->
            try {
                settings.onActivity { activity ->
                    val entry = activity.findViewById<View>(R.id.settings_ad_debug_entry)
                    assertNotNull("DEBUG settings entry must exist in a debug build", entry)
                    entry.performClick()
                }
                waitUntil("DEBUG tools did not open") {
                    onResumedTools { activity -> activity.findViewById<Spinner>(R.id.ad_debug_source).isEnabled }
                }
                instrumentation.runOnMainSync {
                    val activity = resumed(AdDebugActivity::class.java)!!
                    assertEquals("MAX", activity.findViewById<Spinner>(R.id.ad_debug_source).selectedItem.toString())
                    assertFalse(activity.findViewById<SwitchMaterial>(R.id.ad_debug_global_enabled).isChecked)
                    assertNoRequestOrPreview(activity)
                }
                // 验证打开 Spinner 没有把已保存 MAX 隐式写回其第一个选项 BIDDING。
                assertEquals(configured, readControls())
                screenshot("settings_entry_existing_max_off")
            } finally {
                finishTools()
            }
        }
    }

    @Test
    fun sourceAndMasterUseRealSdkPersistenceAndFormatSurvivesRecreation() = preservingSdkControls {
        val initial = Controls(AdSource.GAM, false)
        writeControls(initial)
        ActivityScenario.launch(AdDebugActivity::class.java).use { scenario ->
            awaitControls(scenario, initial)
            scenario.onActivity { it.findViewById<Spinner>(R.id.ad_debug_source).setSelection(5) }
            awaitControls(scenario, Controls(AdSource.MAX, false))
            assertEquals(AdSource.MAX, readControls().source)

            scenario.onActivity { it.findViewById<SwitchMaterial>(R.id.ad_debug_global_enabled).isChecked = true }
            awaitControls(scenario, Controls(AdSource.MAX, true))
            scenario.onActivity { it.findViewById<Spinner>(R.id.ad_debug_format).setSelection(5) }
            waitUntil("Banner selection did not settle") {
                var selected = false
                scenario.onActivity {
                    selected = it.findViewById<Spinner>(R.id.ad_debug_format).selectedItemPosition == 5 &&
                        it.findViewById<Spinner>(R.id.ad_debug_format).isEnabled
                }
                selected
            }

            scenario.recreate()
            awaitControls(scenario, Controls(AdSource.MAX, true))
            scenario.onActivity { activity ->
                assertEquals(5, activity.findViewById<Spinner>(R.id.ad_debug_format).selectedItemPosition)
                assertNoRequestOrPreview(activity)
            }
            screenshot("saved_max_banner_after_recreation")

            scenario.onActivity { it.findViewById<View>(R.id.ad_debug_reset).performClick() }
            awaitControls(scenario, Controls(AdSource.BIDDING, true))
            assertEquals(Controls(AdSource.BIDDING, true), readControls())
            scenario.onActivity { activity ->
                assertEquals(5, activity.findViewById<Spinner>(R.id.ad_debug_format).selectedItemPosition)
                assertEquals(
                    activity.getString(R.string.ad_debug_state_restored),
                    activity.findViewById<TextView>(R.id.ad_debug_result).text.toString(),
                )
                assertNoPreviewActivity()
            }
            screenshot("restored_sdk_defaults")
        }
    }

    @Test
    fun globalOffKeepsShowDisabledAcrossRefreshAndRecreationWithoutOpeningPreview() = preservingSdkControls {
        val disabled = Controls(AdSource.ADMOB, false)
        writeControls(disabled)
        ActivityScenario.launch(AdDebugActivity::class.java).use { scenario ->
            awaitControls(scenario, disabled)
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.ad_debug_show).isEnabled)
                assertNoRequestOrPreview(activity)
                activity.findViewById<View>(R.id.ad_debug_refresh).performClick()
            }
            awaitControls(scenario, disabled)
            scenario.recreate()
            awaitControls(scenario, disabled)
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.ad_debug_show).isEnabled)
                assertNoRequestOrPreview(activity)
            }
            assertEquals(disabled, readControls())
            screenshot("global_off_no_auto_request")
        }
    }

    @Test
    fun englishAndChineseToolsAndPreviewFit320dpAtDoubleFontScaleWithOneScrollContainer() {
        for (tag in listOf("en", "zh-Hans")) {
            val bitmaps = mutableListOf<Pair<String, Bitmap>>()
            instrumentation.runOnMainSync {
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = 320
                    screenHeightDp = 720
                    densityDpi = 160
                    fontScale = 2f
                    setLocale(Locale.forLanguageTag(tag))
                }
                val themed = ContextThemeWrapper(
                    context.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage,
                )
                val inflater = LayoutInflater.from(themed)
                val shell = ScreenSettingsShellBinding.inflate(inflater)
                shell.settingsTitle.setText(R.string.settings_ad_debug)
                val tools = ViewAdDebugBinding.inflate(inflater, shell.settingsBody, true)
                tools.adDebugSource.adapter = ArrayAdapter(
                    themed, R.layout.debug_ad_spinner_item, listOf("BIDDING", "MAX"),
                ).apply { setDropDownViewResource(R.layout.debug_ad_spinner_dropdown_item) }
                tools.adDebugFormat.adapter = ArrayAdapter(
                    themed, R.layout.debug_ad_spinner_item,
                    listOf(themed.getString(R.string.ad_debug_format_full_screen_native)),
                ).apply { setDropDownViewResource(R.layout.debug_ad_spinner_dropdown_item) }
                tools.adDebugDiagnostics.text = "SDK initialization=READY\nGlobal ads enabled=false\nSource=MAX"
                measureScreen(shell.root)
                assertEquals("$tag: only the shared settings scroll container is allowed", 1,
                    descendants(shell.root).count { it is NestedScrollView })
                assertTrue("$tag: tools must remain scrollable at large fonts", tools.root.height > shell.settingsScroll.height)
                assertReadableText(shell.settingsTitle, tag)
                descendants(tools.root).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }
                    .forEach { assertReadableText(it, tag) }
                assertTouchTargets(tools.root, tag)
                bitmaps += "tools_${tag}_320_font2_screen" to draw(shell.root)
                bitmaps += "tools_${tag}_320_font2_content" to draw(tools.root)

                val previewShell = ScreenSettingsShellBinding.inflate(inflater)
                previewShell.settingsTitle.setText(R.string.ad_debug_preview_title)
                val preview = ViewAdDebugPreviewBinding.inflate(inflater, previewShell.settingsBody, true)
                preview.adDebugPreviewProgress.visibility = View.GONE
                preview.adDebugPreviewResult.setText(R.string.ad_debug_interrupted)
                measureScreen(previewShell.root)
                assertEquals(1, descendants(previewShell.root).count { it is NestedScrollView })
                assertTouchTargets(preview.root, tag)
                descendants(preview.root).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }
                    .forEach { assertReadableText(it, tag) }
                bitmaps += "preview_${tag}_320_font2_screen" to draw(previewShell.root)
            }
            bitmaps.forEach { (name, bitmap) -> save(bitmap, name) }
        }
    }

    private data class Controls(val source: AdSource, val enabled: Boolean)

    private fun readControls(): Controls = runBlocking(Dispatchers.IO) {
        Controls(AdSourceController.getCurrentSource(), GlobalAdSwitchInterceptor.isGlobalAdEnabled())
    }

    private fun writeControls(controls: Controls) = runBlocking(Dispatchers.IO) {
        AdSourceController.setCurrentSource(controls.source)
        if (controls.enabled) GlobalAdSwitchInterceptor.enableGlobalAd()
        else GlobalAdSwitchInterceptor.disableGlobalAd()
    }

    private fun preservingSdkControls(block: () -> Unit) {
        val original = readControls()
        try {
            block()
        } finally {
            // 先关闭页面及其观察，再恢复测试真正修改的两个 SDK 持久参数。
            finishTools()
            writeControls(original)
            assertEquals("SDK controls must be restored even if a UI assertion fails", original, readControls())
        }
    }

    private fun awaitControls(scenario: ActivityScenario<AdDebugActivity>, expected: Controls) {
        waitUntil("SDK controls did not settle at $expected") {
            val actual = readControls()
            var matched = false
            scenario.onActivity { activity ->
                val source = activity.findViewById<Spinner>(R.id.ad_debug_source)
                val master = activity.findViewById<SwitchMaterial>(R.id.ad_debug_global_enabled)
                matched = source.isEnabled && master.isEnabled &&
                    source.selectedItem?.toString() == expected.source.name && master.isChecked == expected.enabled
            }
            actual == expected && matched
        }
    }

    private fun assertNoRequestOrPreview(activity: Activity) {
        assertEquals(activity.getString(R.string.ad_debug_no_result),
            activity.findViewById<TextView>(R.id.ad_debug_result).text.toString())
        assertNoPreviewActivity()
    }

    private fun assertNoPreviewActivity() {
        assertFalse("Settings and selection must not automatically launch an ad preview", liveActivities()
            .any { it is AdDebugPreviewActivity })
    }

    private fun liveActivities(): List<Activity> = Stage.entries.filter { it != Stage.DESTROYED }
        .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }

    private fun finishTools() {
        instrumentation.runOnMainSync {
            liveActivities().filter { it is AdDebugActivity || it is AdDebugPreviewActivity }.distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        waitUntil("DEBUG pages did not release their lifecycle before restoring SDK controls") {
            var finished = false
            instrumentation.runOnMainSync {
                finished = liveActivities().none { it is AdDebugActivity || it is AdDebugPreviewActivity }
            }
            finished
        }
    }

    private fun onResumedTools(predicate: (AdDebugActivity) -> Boolean): Boolean {
        var matched = false
        instrumentation.runOnMainSync { resumed(AdDebugActivity::class.java)?.let { matched = predicate(it) } }
        return matched
    }

    private fun <T : Activity> resumed(type: Class<T>): T? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull { type.isInstance(it) && it.hasWindowFocus() }?.let(type::cast)

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(25)
        assertTrue(message, condition())
    }

    private fun measureScreen(root: View) {
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun assertReadableText(text: TextView, tag: String) {
        val label = "$tag: ${text.text}"
        assertNotNull("$label must have a measured text layout", text.layout)
        val layout = text.layout!!
        assertTrue("$label vertical crop", text.height >= layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
        repeat(layout.lineCount) { line -> assertEquals("$label must not ellipsize", 0, layout.getEllipsisCount(line)) }
    }

    private fun assertTouchTargets(root: View, tag: String) {
        descendants(root).filterIsInstance<MaterialButton>().forEach { button ->
            assertTrue("$tag: ${button.text} touch height must be 48dp", button.height >= 48)
            assertReadableText(button, tag)
        }
        descendants(root).filter { it is Spinner || it is SwitchMaterial }.forEach { control ->
            assertTrue("$tag: selector/switch touch height must be 48dp", control.height >= 48)
        }
    }

    private fun descendants(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(descendants(root.getChildAt(index)))
    }

    private fun draw(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        .also { bitmap -> Canvas(bitmap).apply { drawColor(context.getColor(R.color.home_background)); view.draw(this) } }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Device screenshot must be available", bitmap)
        save(bitmap!!, name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        try {
            val folder = File(context.getExternalFilesDir(null), "ad-debug-tests").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
