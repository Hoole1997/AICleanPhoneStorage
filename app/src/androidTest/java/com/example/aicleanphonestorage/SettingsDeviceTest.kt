package com.example.aicleanphonestorage

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.locale.AppLanguages
import com.example.aicleanphonestorage.databinding.ScreenSettingsShellBinding
import com.example.aicleanphonestorage.databinding.ViewSettingsMenuBinding
import com.example.aicleanphonestorage.feature.settings.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅验证页面、草稿和 Intent；不访问隐私网页、不发送邮件、不修改设备语言。 */
@RunWith(AndroidJUnit4::class)
class SettingsDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun homeEntryAndInternalDestinationsWork() {
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            try {
                home.onActivity { it.findViewById<View>(R.id.settings_button).performClick() }
                waitFor(SettingsActivity::class.java)
                screenshot("settings")
                onView(withId(R.id.settings_language)).perform(click())
                waitFor(LanguageSettingsActivity::class.java)
                onView(withId(R.id.language_list)).check(matches(isDisplayed()))
                onView(withId(R.id.settings_back)).perform(click())
                waitFor(SettingsActivity::class.java)
                onView(withId(R.id.settings_about)).perform(click())
                waitFor(AboutActivity::class.java)
                waitUntil {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready =
                            resumed(AboutActivity::class.java)
                                ?.findViewById<View>(R.id.settings_info_version)
                                ?.visibility == View.VISIBLE
                    }
                    ready
                }
                onView(withId(R.id.settings_info_heading))
                    .check(matches(withText(R.string.app_name)))
                screenshot("about")
                onView(withId(R.id.settings_back)).perform(click())
                waitFor(SettingsActivity::class.java)
                onView(withId(R.id.settings_feedback)).perform(click())
                waitFor(FeedbackActivity::class.java)
                onView(withId(R.id.feedback_send)).check(matches(isNotEnabled()))
                screenshot("feedback")
                onView(withId(R.id.settings_back)).perform(click())
                waitFor(SettingsActivity::class.java)
            } finally {
                instrumentation.runOnMainSync {
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filter { it.javaClass.packageName.endsWith("settings") }
                        .forEach { it.finish() }
                }
            }
        }
    }

    @Test
    fun feedbackDraftSurvivesRecreationAndDestinationsStayEncoded() {
        val message = "Less clutter & more detail?\nA new idea #1"
        ActivityScenario.launch(FeedbackActivity::class.java).use { scenario ->
            onView(withId(R.id.feedback_message)).perform(replaceText(message), closeSoftKeyboard())
            onView(withId(R.id.feedback_send)).check(matches(isEnabled()))
            scenario.recreate()
            onView(withId(R.id.feedback_message)).check(matches(withText(message)))
            onView(withId(R.id.feedback_send)).check(matches(isEnabled()))
        }
        val draft =
            SettingsDestinations.feedback(
                BuildConfig.FEEDBACK_EMAIL,
                "Test subject",
                message,
            )!!
        assertEquals(Intent.ACTION_SENDTO, draft.action)
        assertEquals("mailto", draft.data!!.scheme)
        assertEquals(BuildConfig.FEEDBACK_EMAIL.trim(), draft.data!!.schemeSpecificPart.substringBefore("?"))
        assertEquals(message, draft.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(draft.dataString!!.contains("%26"))
        assertFalse(draft.dataString!!.contains("\n"))
        assertNull(SettingsDestinations.feedback("bad\naddress", "test", message))
        assertNull(SettingsDestinations.feedback("feedback@example.com", "test", " "))
        val privacy =
            SettingsDestinations.privacy(BuildConfig.PRIVACY_URL)!!
        assertEquals(Intent.ACTION_VIEW, privacy.action)
        assertEquals(BuildConfig.PRIVACY_URL.trim(), privacy.dataString)
        assertNull(SettingsDestinations.privacy("javascript:alert(1)"))
    }

    @Test
    fun menuHasFiveReadableTouchTargetsAndCurrentLanguageAtLargeFontSizes() {
        for (tag in listOf("en", "zh-Hans", "ar", "de", "pt-BR")) for (scale in listOf(1f, 2f)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val configuration =
                    Configuration(context.resources.configuration).apply {
                        fontScale = scale
                        setLocale(java.util.Locale.forLanguageTag(tag))
                    }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(configuration),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val binding = ViewSettingsMenuBinding.inflate(LayoutInflater.from(themed))
                binding.settingsLanguage.setValue(
                    themed.getString(R.string.settings_current_language)
                )
                val density = themed.resources.displayMetrics.density
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        (288 * density).toInt(),
                        View.MeasureSpec.EXACTLY,
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        (1000 * density).toInt(),
                        View.MeasureSpec.AT_MOST,
                    ),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                for (row in
                    listOf(
                        binding.settingsNotifications,
                        binding.settingsLanguage,
                        binding.settingsPrivacy,
                        binding.settingsFeedback,
                        binding.settingsAbout,
                    )) {
                    assertTrue(row.height >= 48 * density)
                    val title = row.findViewById<TextView>(R.id.settings_item_title)
                    val icon = row.findViewById<View>(R.id.settings_item_icon)
                    val arrow = row.findViewById<View>(R.id.settings_item_arrow)
                    assertTrue(title.height >= title.layout.height)
                    val titleBounds = Rect(0, 0, title.width, title.height)
                    row.offsetDescendantRectToMyCoords(title, titleBounds)
                    assertTrue("$tag: icon overlaps title", icon.right <= titleBounds.left)
                    assertTrue("$tag: title overlaps arrow", titleBounds.right <= arrow.left)
                    if (row === binding.settingsLanguage) {
                        val value = row.findViewById<TextView>(R.id.settings_item_value)
                        val valueBounds = Rect(0, 0, value.width, value.height)
                        row.offsetDescendantRectToMyCoords(value, valueBounds)
                        assertFalse(
                            "$tag: title overlaps current language",
                            Rect.intersects(titleBounds, valueBounds),
                        )
                        assertTrue(value.height >= value.layout.height)
                        assertTrue(valueBounds.right <= arrow.left)
                        assertTrue(row.contentDescription.contains(value.text))
                    }
                    assertEquals(View.LAYOUT_DIRECTION_LTR, row.layoutDirection)
                }
                bitmap =
                    Bitmap.createBitmap(
                            binding.root.width,
                            binding.root.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        .also { binding.root.draw(Canvas(it)) }
            }
            save(bitmap!!, "menu_${tag}_$scale")
        }
    }

    @Test
    fun languageSummaryUsesResolvedResourceLocaleIncludingSystemFallback() {
        for (language in AppLanguages.supported) {
            val configured =
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply {
                        setLocale(java.util.Locale.forLanguageTag(language.tag))
                    }
                )
            assertEquals(
                language.nativeName,
                configured.getString(R.string.settings_current_language),
            )
        }
        val unsupported =
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(java.util.Locale.ITALIAN)
                }
            )
        assertEquals("English", unsupported.getString(R.string.settings_current_language))
    }

    @Test
    fun settingsSummaryUpdatesAfterLanguageChangeAndRecreation() {
        val languages = (context.applicationContext as CleanApplication).languages
        runBlocking { languages.ready.await() }
        val before = languages.selected.value
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            try {
                for (tag in listOf("en", "zh-Hans", "pt-BR", "")) {
                    runBlocking { languages.select(tag) }
                    waitUntil {
                        var matched = false
                        instrumentation.runOnMainSync {
                            val activity = resumed(SettingsActivity::class.java)
                            val value =
                                activity
                                    ?.findViewById<View>(R.id.settings_language)
                                    ?.findViewById<TextView>(R.id.settings_item_value)
                                    ?.text
                                    ?.toString()
                            val expected =
                                if (tag.isEmpty())
                                    activity?.getString(R.string.settings_current_language)
                                else AppLanguages.supported.single { it.tag == tag }.nativeName
                            matched =
                                value != null &&
                                    value == expected &&
                                    languages.selected.value == tag
                        }
                        matched
                    }
                    screenshot("current_language_${tag.ifEmpty { "system" }}")
                }
                scenario.recreate()
                waitFor(SettingsActivity::class.java)
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.settings_current_language),
                        activity
                            .findViewById<View>(R.id.settings_language)
                            .findViewById<TextView>(R.id.settings_item_value)
                            .text
                            .toString(),
                    )
                }
            } finally {
                runBlocking { languages.select(before.takeIf(AppLanguages::valid).orEmpty()) }
            }
        }
    }

    @Test
    fun figmaLayoutUsesOriginalIconSizesAndSpacing() {
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val configured =
                context.createConfigurationContext(
                    Configuration(context.resources.configuration).apply {
                        fontScale = 1f
                        setLocale(java.util.Locale.US)
                    }
                )
            val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
            val density = themed.resources.displayMetrics.density
            val screen = ScreenSettingsShellBinding.inflate(LayoutInflater.from(themed))
            screen.settingsTitle.setText(R.string.home_settings)
            screen.settingsContent.setPadding(0, (44 * density).toInt(), 0, 0)
            screen.settingsBody.setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (24 * density).toInt(),
            )
            val menu =
                ViewSettingsMenuBinding.inflate(
                    LayoutInflater.from(themed),
                    screen.settingsBody,
                    true,
                )
            menu.settingsLanguage.setValue(themed.getString(R.string.settings_current_language))
            val width = (375 * density).toInt()
            val height = (812 * density).toInt()
            screen.root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            screen.root.layout(0, 0, width, height)
            assertEquals(296f, menu.root.height / density, 2f)
            val card = Rect(0, 0, menu.root.width, menu.root.height)
            screen.root.offsetDescendantRectToMyCoords(menu.root, card)
            assertEquals(104f, card.top / density, 1f)
            assertEquals(16f, card.left / density, 1f)
            assertEquals(
                24f,
                menu.settingsLanguage.findViewById<View>(R.id.settings_item_icon).width / density,
                1f,
            )
            assertEquals(
                16f,
                menu.settingsLanguage.findViewById<View>(R.id.settings_item_arrow).width / density,
                1f,
            )
            bitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply {
                        drawColor(android.graphics.Color.rgb(246, 246, 246))
                        screen.root.draw(this)
                    }
                }
        }
        save(bitmap!!, "figma_375")
    }

    private fun <T : AppCompatActivity> resumed(type: Class<T>): T? =
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .firstOrNull { type.isInstance(it) && it.hasWindowFocus() }
            ?.let(type::cast)

    private fun <T : AppCompatActivity> waitFor(type: Class<T>) = waitUntil {
        var found = false
        instrumentation.runOnMainSync { found = resumed(type) != null }
        found
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (!predicate() && SystemClock.uptimeMillis() < until) SystemClock.sleep(25)
        assertTrue("Settings UI did not settle", predicate())
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot()?.let { save(it, name) }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val folder = File(context.getExternalFilesDir(null), "settings-tests").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
