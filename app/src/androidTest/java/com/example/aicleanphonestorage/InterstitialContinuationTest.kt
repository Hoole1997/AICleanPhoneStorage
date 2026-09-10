package com.example.aicleanphonestorage

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 假请求仅测试本应用续接机制，不请求/点击真实广告。生产请求仍使用用户提供的 AdExt。 */
@RunWith(AndroidJUnit4::class)
class InterstitialContinuationTest {
    @Test fun failureAndDuplicateCallbackContinueExactlyOnceAfterResume() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var requests = 0
            var operations = 0
            lateinit var complete: (Boolean) -> Unit
            scenario.onActivity { activity ->
                val ads = InterstitialActions(activity) { _, call -> requests++; complete = call }
                ads.register("clean") { operations++ }
                ads.run("clean", "test"); ads.run("clean", "test")
                assertEquals(1, requests)
                assertEquals(0, operations)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { complete(false); complete(true); assertEquals(0, operations) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(1, operations); complete(false); assertEquals(1, operations) }
        }
    }

    @Test fun recreatedActivityUsesItsOwnHandlerWithoutRequestingAnotherAd() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var requests = 0
            var oldCalls = 0
            var newCalls = 0
            lateinit var complete: (Boolean) -> Unit
            scenario.onActivity { activity ->
                val ads = InterstitialActions(activity) { _, call -> requests++; complete = call }
                ads.register("clean") { oldCalls++ }
                ads.run("clean", "test")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val ads = InterstitialActions(activity) { _, _ -> requests++ }
                ads.register("clean") { newCalls++ }
                complete(true); complete(false)
                assertEquals(1, requests)
                assertEquals(0, oldCalls)
                assertEquals(1, newCalls)
            }
        }
    }
}
