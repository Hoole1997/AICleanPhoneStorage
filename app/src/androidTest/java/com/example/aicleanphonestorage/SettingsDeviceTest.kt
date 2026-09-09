package com.example.aicleanphonestorage

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
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
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.databinding.ViewSettingsMenuBinding
import com.example.aicleanphonestorage.feature.settings.*
import java.io.File
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
                onView(withId(R.id.settings_info_heading))
                    .check(matches(withText(R.string.settings_language_pending_title)))
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
                context.getString(R.string.settings_support_email),
                "Test subject",
                message,
            )!!
        assertEquals(Intent.ACTION_SENDTO, draft.action)
        assertEquals("mailto", draft.data!!.scheme)
        assertEquals("feedback@example.com", draft.data!!.schemeSpecificPart.substringBefore("?"))
        assertEquals(message, draft.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(draft.dataString!!.contains("%26"))
        assertFalse(draft.dataString!!.contains("\n"))
        assertNull(SettingsDestinations.feedback("bad\naddress", "test", message))
        assertNull(SettingsDestinations.feedback("feedback@example.com", "test", " "))
        val privacy =
            SettingsDestinations.privacy(context.getString(R.string.settings_privacy_url))!!
        assertEquals(Intent.ACTION_VIEW, privacy.action)
        assertEquals("https://example.com/privacy", privacy.dataString)
        assertNull(SettingsDestinations.privacy("javascript:alert(1)"))
    }

    @Test
    fun menuHasFourReadableTouchTargetsAtNormalAndDoubleFontSize() {
        for (scale in listOf(1f, 2f)) {
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val configuration =
                    Configuration(context.resources.configuration).apply { fontScale = scale }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(configuration),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val binding = ViewSettingsMenuBinding.inflate(LayoutInflater.from(themed))
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
                        binding.settingsLanguage,
                        binding.settingsPrivacy,
                        binding.settingsFeedback,
                        binding.settingsAbout,
                    )) {
                    assertTrue(row.height >= 48 * density)
                    assertTrue(
                        row.height >=
                            row.layout.height + row.compoundPaddingTop + row.compoundPaddingBottom
                    )
                }
                bitmap =
                    Bitmap.createBitmap(
                            binding.root.width,
                            binding.root.height,
                            Bitmap.Config.ARGB_8888,
                        )
                        .also { binding.root.draw(Canvas(it)) }
            }
            save(bitmap!!, "menu_$scale")
        }
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
