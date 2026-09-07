package com.example.aicleanphonestorage

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aicleanphonestorage.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 只验证 UI/预览与生命周期；不申请权限，也不读写用户文件。 */
@RunWith(AndroidJUnit4::class)
class HomeLifecycleTest {
    private fun launch(mode: String = "initial"): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("home_preview", mode))

    @Test
    fun initialStateSurvivesActivityRecreation() {
        launch().use { scenario ->
            assertHero(R.string.home_storage_used, "40.3")
            scenario.recreate()
            assertHero(R.string.home_storage_used, "40.3")
        }
    }

    @Test
    fun returningFromStoppedStateCollectsOverviewAgain() {
        launch().use { scenario ->
            assertHero(R.string.home_storage_used, "40.3")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertHero(R.string.home_storage_used, "40.3")
        }
    }

    @Test
    fun completedPreviewSurvivesRecreationWithoutStartingScan() {
        launch("scanned").use { scenario ->
            assertHero(R.string.home_scan_complete, "199")
            onView(withId(R.id.clean_button)).perform(click())
            assertHero(R.string.home_scan_complete, "199")
            scenario.recreate()
            assertHero(R.string.home_scan_complete, "199")
        }
    }

    @Test
    fun allSevenToolsExistAndLastToolIsReachable() {
        launch().use { scenario ->
            assertHero(R.string.home_storage_used, "40.3")
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.home_list)
                assertEquals(10, list.adapter?.itemCount) // 主卡、统计条、标题 + 七张工具卡。
                list.scrollToPosition(9)
            }
            onView(withText(R.string.home_tool_screenshots)).check(matches(isDisplayed()))
            onView(withText(R.string.home_tool_screenshots)).perform(click())
            onView(withText(R.string.home_tool_screenshots)).check(matches(isDisplayed()))
        }
    }

    private fun assertHero(title: Int, value: String) {
        onView(withId(R.id.summary_title)).check(matches(withText(title)))
        onView(withId(R.id.summary_value)).check(matches(withText(value)))
    }
}
