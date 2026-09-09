package com.example.aicleanphonestorage

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.locale.AppLanguages
import com.example.aicleanphonestorage.feature.settings.LanguageSettingsActivity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅切换本应用语言，结束恢复原选择；不修改系统语言或用户权限。可在 API 32/33+ 复用。 */
@RunWith(AndroidJUnit4::class)
class LanguageDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val languages
        get() = (context.applicationContext as CleanApplication).languages

    @Test
    fun switchingLocalesRecreatesPagesAndSystemClearsOverride() {
        runBlocking { languages.ready.await() }
        val before = languages.selected.value
        ActivityScenario.launch(LanguageSettingsActivity::class.java).use { scenario ->
            try {
                for (tag in listOf("zh-Hans", "hi", "ar", "ur", "pt-BR", "")) {
                    selectRow(tag)
                    waitUntil {
                        var ready = false
                        instrumentation.runOnMainSync {
                            val activity = active()
                            ready =
                                activity != null &&
                                    languages.selected.value == tag &&
                                    activity.resources.configuration.locales[0].language ==
                                        (if (tag.isEmpty())
                                            Resources.getSystem().configuration.locales[0].language
                                        else Locale.forLanguageTag(tag).language)
                        }
                        ready
                    }
                    instrumentation.runOnMainSync {
                        val activity = active()!!
                        assertEquals(
                            View.LAYOUT_DIRECTION_LTR,
                            activity.window.decorView.layoutDirection,
                        )
                        assertEquals(
                            0,
                            activity.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL,
                        )
                    }
                    screenshot(if (tag.isEmpty()) "system" else tag)
                }
                scenario.recreate()
                waitUntil {
                    var ok = false
                    instrumentation.runOnMainSync { ok = active() != null }
                    ok
                }
                assertEquals("", languages.selected.value)
            } finally {
                runBlocking { languages.select(before.takeIf(AppLanguages::valid).orEmpty()) }
            }
        }
    }

    @Test
    fun eachLanguageLoadsFormattedStringsAndQuantityResources() {
        for (language in AppLanguages.supported) {
            val configuration =
                android.content.res.Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language.tag))
                }
            val localized = context.createConfigurationContext(configuration)
            assertTrue(localized.getString(R.string.settings_language).isNotBlank())
            assertTrue(
                localized.getString(R.string.cleanup_delete_confirm, 3, "12 MB").contains("12 MB")
            )
            for (count in listOf(0, 1, 2, 3, 11, 100)) assertTrue(
                localized.resources
                    .getQuantityString(R.plurals.home_app_count, count, count)
                    .isNotBlank()
            )
        }
    }

    /** Run prepare/verify in separate instrumentation processes around a force-stop. */
    @Test
    fun coldStartFixture() {
        val mode = InstrumentationRegistry.getArguments().getString("languageFixture")
        org.junit.Assume.assumeTrue(
            "Cold-start fixture requires prepare/verify mode",
            mode == "prepare" || mode == "verify",
        )
        ActivityScenario.launch(LanguageSettingsActivity::class.java).use {
            if (mode == "prepare") {
                runBlocking { languages.select("fr") }
                waitUntil { languages.selected.value == "fr" }
            } else {
                try {
                    waitUntil {
                        var ok = false
                        instrumentation.runOnMainSync {
                            ok =
                                active()?.resources?.configuration?.locales?.get(0)?.language ==
                                    "fr"
                        }
                        ok
                    }
                    assertEquals("fr", languages.selected.value)
                } finally {
                    runBlocking { languages.select("") }
                }
            }
        }
    }

    private fun active(): LanguageSettingsActivity? =
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<LanguageSettingsActivity>()
            .firstOrNull { it.hasWindowFocus() }

    private fun selectRow(tag: String) {
        val position =
            if (tag.isEmpty()) 0 else AppLanguages.supported.indexOfFirst { it.tag == tag } + 1
        waitUntil {
            var ok = false
            instrumentation.runOnMainSync {
                ok =
                    active()?.findViewById<RecyclerView>(R.id.language_list)?.adapter?.itemCount ==
                        17
            }
            ok
        }
        instrumentation.runOnMainSync {
            active()!!.findViewById<RecyclerView>(R.id.language_list).scrollToPosition(position)
        }
        waitUntil {
            var ok = false
            instrumentation.runOnMainSync {
                ok =
                    active()
                        ?.findViewById<RecyclerView>(R.id.language_list)
                        ?.findViewHolderForAdapterPosition(position) != null
            }
            ok
        }
        instrumentation.runOnMainSync {
            active()!!
                .findViewById<RecyclerView>(R.id.language_list)
                .findViewHolderForAdapterPosition(position)!!
                .itemView
                .performClick()
        }
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        var stableSince = 0L
        while (SystemClock.uptimeMillis() < deadline) {
            val now = SystemClock.uptimeMillis()
            if (check()) {
                if (stableSince == 0L) stableSince = now
                if (now - stableSince >= 100) return
            } else stableSince = 0L
            SystemClock.sleep(25)
        }
        fail("Language did not settle; selected=" + languages.selected.value)
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val folder = File(context.getExternalFilesDir(null), "language-tests").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            File(folder, "$name.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
    }
}
