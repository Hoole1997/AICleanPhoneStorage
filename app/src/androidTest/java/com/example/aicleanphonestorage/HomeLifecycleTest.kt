package com.example.aicleanphonestorage

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aicleanphonestorage.app.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/** 验证真实 Activity 生命周期；扫描业务未接入，不读取或修改测试设备上的用户文件。 */
@RunWith(AndroidJUnit4::class)
class HomeLifecycleTest {
    @Test
    fun initialStateSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotScanned()
            scenario.recreate()
            assertNotScanned()
        }
    }

    @Test
    fun returningFromStoppedStateCollectsOverviewAgain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotScanned()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertNotScanned()
        }
    }

    private fun assertNotScanned() {
        onView(withId(R.id.scan_status)).check(matches(isDisplayed()))
        onView(withId(R.id.scan_status)).check(matches(withText(R.string.home_not_scanned)))
        onView(withId(R.id.summary_value)).check(matches(withText(R.string.home_unknown_value)))
    }
}
