package com.example.aicleanphonestorage

import android.app.Activity
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.ui.loading.TaskLoadingDialogFragment
import com.example.aicleanphonestorage.feature.networktraffic.data.UsageAccessChecker
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficActivity
import com.example.aicleanphonestorage.feature.networktraffic.ui.UsageAccessSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 使用用户已授予的权限验证真实流程；不通过 appops/pm 修改系统授权。 */
@RunWith(AndroidJUnit4::class)
class TrafficFeatureDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun homeShowsLoadingBeforeDestinationAndDatesNeverOpenDialog() {
        assumeTrue(UsageAccessChecker(ApplicationProvider.getApplicationContext()).isGranted())
        var destination: NetworkTrafficActivity? = null
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            try {
                onView(withText(R.string.home_tool_network)).perform(click())
                onView(withId(R.id.loading_title)).check(matches(isDisplayed()))
                home.onActivity { assertNotNull(it.supportFragmentManager.findFragmentByTag(TaskLoadingDialogFragment.TAG)) }
                assertNull(resumedTraffic())
                waitUntil { resumedTraffic()?.also { destination = it } != null }
                onView(withId(R.id.period_previous)).perform(click())
                repeat(20) {
                    instrumentation.runOnMainSync { assertNull(destination!!.supportFragmentManager.findFragmentByTag(TaskLoadingDialogFragment.TAG)) }
                    SystemClock.sleep(50) // instrumentation 线程，不阻塞应用主线程。
                }
                waitUntil {
                    var done = false
                    instrumentation.runOnMainSync { done = destination!!.findViewById<View>(R.id.traffic_refresh_indicator).visibility != View.VISIBLE }
                    done
                }
                onView(withId(R.id.period_previous)).check(matches(isChecked()))
            } finally { instrumentation.runOnMainSync { destination?.finish() } }
        }
    }

    @Test fun closingEntryLoadingDoesNotNavigateLater() {
        assumeTrue(UsageAccessChecker(ApplicationProvider.getApplicationContext()).isGranted())
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            onView(withText(R.string.home_tool_network)).perform(click())
            onView(withId(R.id.loading_close)).perform(click())
            SystemClock.sleep(4200)
            assertNull(resumedTraffic())
            home.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(TaskLoadingDialogFragment.TAG)) }
        }
    }

    @Test fun usageAccessIntentTargetsCurrentApplication() {
        val context = instrumentation.targetContext
        val intent = UsageAccessSettings.detailsIntent(context.packageName)
        assertEquals("package", intent.data?.scheme)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
        // 真机公共路由可解析；不点击开关、不改用户授权。
        assertNotNull(intent.resolveActivity(context.packageManager))
    }

    private fun resumedTraffic(): NetworkTrafficActivity? {
        var result: NetworkTrafficActivity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<NetworkTrafficActivity>().firstOrNull()
        }
        return result
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue("UI did not reach expected state within timeout", condition())
    }
}
