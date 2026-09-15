package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.rating.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 使用假 ReviewManager/注入回调，不提交真实 Google Play 评价；持久化使用独立测试文件。 */
@RunWith(AndroidJUnit4::class)
class RatingPromptDeviceTest {
    @get:org.junit.Rule val noHotAds = NoHotStartAdsRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun permissionFlowMustFinishAndPlayStartsOnlyAfterPromptCloses() {
        for (stars in listOf(2, 5)) ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            var permissionsComplete = false
            var claims = 0
            var reviews = 0
            val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
            lateinit var model: RatingPromptViewModel
            lateinit var coordinator: RatingPromptCoordinator
            scenario.onActivity { activity ->
                model = RatingPromptViewModel({ claims++; true }, SavedStateHandle()) { e, p -> events += e to p }
                activity.viewModelStore.put("rating-test", model)
                coordinator = RatingPromptCoordinator(activity, activity.findViewById(android.R.id.content), model,
                    ready = { permissionsComplete }, review = { host ->
                        assertNull(host.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG))
                        reviews++
                        GooglePlayReviewLauncher { FakeReviewManager(it) }.launch(host)
                    }, blocked = { false })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { coordinator.drain(); assertEquals(0, claims) }
            scenario.onActivity { permissionsComplete = true; coordinator.drain() }
            waitUntil { var shown = false; scenario.onActivity { shown = it.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG)?.isResumed == true }; shown }
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG)!!.requireView()
                assertTrue(root.findViewById<View>(R.id.rating_star_5).isSelected)
                assertTrue(root.findViewById<View>(R.id.rating_submit).isEnabled)
                save(root, "rating-default")
            }
            onView(withId(if (stars == 2) R.id.rating_star_2 else R.id.rating_star_5)).perform(click())
            onView(withId(R.id.rating_submit)).perform(click())
            waitUntil { var finished = false; scenario.onActivity { coordinator.windowFocusChanged(it.hasWindowFocus()); finished = model.phase.value == RatingPhase.FINISHED }; finished }
            scenario.onActivity {
                assertEquals(1, claims)
                assertEquals(if (stars >= 4) 1 else 0, reviews)
                assertEquals(listOf(MetricEvent.RATE_SHOW, MetricEvent.RATE_CLICK), events.map { it.first })
                assertEquals(mapOf("stars" to stars, "action" to if (stars >= 4) "store" else "close"), events.last().second)
                coordinator.drain()
                assertNull(it.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG))
            }
        }
    }

    @Test fun rotationKeepsSelectedStarsAndDismissalOnlyReportsOnce() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
            lateinit var model: RatingPromptViewModel
            fun bind(activity: AppCompatActivity) {
                model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        RatingPromptViewModel({ true }, SavedStateHandle()) { e, p -> events += e to p } as T
                })[RatingPromptViewModel::class.java]
                RatingPromptCoordinator(activity, activity.findViewById(android.R.id.content), model,
                    ready = { true }, review = { fail("Dismissal must not launch Play") }, blocked = { false })
            }
            scenario.onActivity(::bind)
            waitUntil { model.phase.value == RatingPhase.SHOWING }
            onView(withId(R.id.rating_star_3)).perform(click())
            scenario.recreate()
            scenario.onActivity(::bind)
            waitUntil { var shown = false; scenario.onActivity { shown = it.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG)?.isResumed == true }; shown }
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(RatingPromptDialog.TAG) as RatingPromptDialog
                assertTrue(fragment.requireView().findViewById<View>(R.id.rating_star_3).isSelected)
                fragment.requireDialog().cancel()
            }
            waitUntil { model.phase.value == RatingPhase.FINISHED }
            assertEquals(1, events.count { it.first == MetricEvent.RATE_SHOW })
            assertEquals(1, events.count { it.first == MetricEvent.RATE_CLICK })
            assertEquals(mapOf("stars" to 3, "action" to "close"), events.last().second)
        }
    }

    @Test fun persistentClaimSurvivesNewStoreInstance() = runBlocking {
        val folder = File(context.cacheDir, "rating-store-${System.nanoTime()}").apply { mkdirs() }
        val file = File(folder, "test.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var store = PersistentRatingPromptStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val results = coroutineScope { List(4) { async { store.claim() } }.awaitAll() }
            assertEquals(1, results.count { it })
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PersistentRatingPromptStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertFalse(store.claim())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); folder.deleteRecursively() }
    }

    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE); view.draw(canvas)
            File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(30)
        assertTrue("Rating flow timed out", condition())
    }
}
