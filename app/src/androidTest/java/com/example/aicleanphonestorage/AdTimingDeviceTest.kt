package com.example.aicleanphonestorage

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.*
import com.example.aicleanphonestorage.core.ui.completion.CompletionActivity
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 请求使用构造注入的假 SDK；删除只操作本用例创建的文件，测试不点击真实广告。 */
@RunWith(AndroidJUnit4::class)
class AdTimingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun homeWaitsForResumeAndDrawAndPreservesSourceWithoutReplay() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var draws = 0
            val positions = mutableListOf<String>()
            lateinit var coordinator: HomeExitAdCoordinator
            lateinit var request: Intent
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                root.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() { draws++ }
                })
                val ads = InterstitialActions(activity) { position, call ->
                    assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    assertTrue(activity.hasWindowFocus())
                    assertTrue("Home content must draw before the request", draws > 0)
                    positions += position
                    call(false)
                }
                coordinator = HomeExitAdCoordinator(activity, root, ads)
                request = HomeExitAdContract.intent(activity, InterstitialPlacements.exit(CleanupFeature.SCREENSHOTS))
                coordinator.accept(Intent(request))
                assertTrue(positions.isEmpty())
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil {
                var shown = false
                scenario.onActivity { coordinator.onWindowFocusChanged(it.hasWindowFocus()); shown = positions.size == 1 }
                shown
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { coordinator.accept(Intent(request)) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertEquals(listOf("back_home_screenshots"), positions)
                assertFalse(coordinator.accept(Intent()))
            }
        }
    }

    @Test fun unknownSourceSkipsAdButStillContinuesOnlyWhenResumed() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var continued = 0
            lateinit var ads: InterstitialActions
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                ads = InterstitialActions(activity) { _, _ -> fail("Unknown source must not request an ad") }
                ads.register("unknown") { continued++ }
                ads.run("unknown", InterstitialPlacements.clean(null))
                assertEquals(0, continued)
                val home = HomeExitAdContract.intent(activity, InterstitialPlacements.exit(null))
                assertNotNull(home.component)
                assertNull(HomeExitAdContract.take(home))
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals(1, continued); assertFalse(ads.busy) }
        }
    }

    @Test fun confirmationCancelDoesNotRequestAdAndCleanupWaitsForCallback() {
        val context = instrumentation.targetContext
        val container = (context.applicationContext as CleanApplication).container
        val index = container.fileScanRepository.index
        val directory = File(context.cacheDir, "ad_timing_${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "sample.pdf").apply { writeBytes(ByteArray(1024)) }
        val scanId = index.start(CleanupFeature.LARGE_FILES)
        val handle = ScanHandle(scanId, CleanupFeature.LARGE_FILES, 1, "Ad timing test")
        try {
            index.insert(scanId, listOf(ScannedFile(
                uri = Uri.fromFile(file).toString(), name = file.name, mime = "application/pdf",
                size = file.length(), modifiedMillis = file.lastModified(), category = FileCategory.DOCUMENTS,
                backend = FileBackend.DIRECT, scope = directory.canonicalPath, path = file.canonicalPath,
            )))
            index.finishScan(handle)
            val filter = CleanupFilter(minimumBytes = 0)
            index.selectAll(handle, filter, true)
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var model: CleanupViewModel
                lateinit var complete: (Boolean) -> Unit
                var requests = 0
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { activity ->
                    model = CleanupViewModel(container.fileScanRepository, container.fileOperations,
                        SavedStateHandle(mapOf("filter.size" to 0L)), scanId)
                    activity.viewModelStore.put("ad.timing.cleanup", model)
                    val ads = InterstitialActions(activity) { position, call ->
                        assertEquals("clean_confirm_large", position)
                        requests++
                        complete = call
                    }
                    val coordinator = CleanupOperationCoordinator(activity, model, null, ads)
                    activity.lifecycleScope.launch { model.state.collect { coordinator.render(it.operation) } }
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitUntil {
                    var ready = false
                    scenario.onActivity { ready = model.state.value.totals.selectedCount == 1 }
                    ready
                }
                fun prepare() {
                    scenario.onActivity { model.prepare() }
                    waitUntil {
                        var shown = false
                        scenario.onActivity { shown = it.supportFragmentManager.findFragmentByTag(CleanupMessageDialog.TAG)?.isResumed == true }
                        shown
                    }
                }
                prepare()
                scenario.onActivity { assertEquals(0, requests) }
                onView(withId(R.id.confirm_cancel)).perform(click())
                scenario.onActivity { assertEquals(0, requests); assertTrue(file.exists()) }
                prepare()
                onView(withId(R.id.confirm_accept)).perform(click())
                scenario.onActivity {
                    assertEquals(1, requests)
                    assertTrue(file.exists())
                    assertTrue(model.state.value.operation is CleanupOperationState.Confirm)
                    complete(false)
                    complete(true)
                }
                waitUntil { !file.exists() }
                scenario.onActivity { assertEquals(1, requests) }
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompletionActivity>().forEach { it.finish() }
            }
            index.discard(scanId)
            directory.deleteRecursively()
        }
    }

    private fun waitUntil(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(30)
        assertTrue("Ad timing flow timed out", check())
    }
}
