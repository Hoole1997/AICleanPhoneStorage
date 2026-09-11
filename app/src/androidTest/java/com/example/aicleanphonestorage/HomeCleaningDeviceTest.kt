package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.ui.completion.*
import com.example.aicleanphonestorage.databinding.ScreenHomeBinding
import com.example.aicleanphonestorage.feature.home.data.*
import com.example.aicleanphonestorage.feature.home.ui.*
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 页面呈现使用明确的测试存储摘要；虚拟量只来自共享状态，不写入真实扫描或删除结果。 */
@RunWith(AndroidJUnit4::class)
class HomeCleaningDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun residentRefreshUpdatesVisibleHomeAndReusesDirtyValue() {
        var now = 1_000L
        var amount = 4567
        val cleaning = HomeCleaningState(true, { now }, { amount })
        val host = CleanNotificationHost(context, cleaning)
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var binding: ScreenHomeBinding
            lateinit var renderer: HomeRenderer
            scenario.onActivity { activity ->
                binding = ScreenHomeBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                renderer = HomeRenderer(binding, HomeUiActions.None)
                val model = HomeViewModel(object : HomeOverviewRepository {
                    override fun observeOverview() = flowOf(HomeOverview(StorageSummary(128_000_000_000, 40_300_000_000), ScanSummary.Completed(999_000_000, 1)))
                }, cleaning.state)
                activity.viewModelStore.put("home.cleaning.test", model)
                activity.lifecycleScope.launch { model.uiState.collect { renderer.render(it) } }
                cleaning.check()
            }
            fun waitValue(value: String) = waitUntil {
                var matches = false
                scenario.onActivity { matches = binding.root.findViewById<TextView>(R.id.summary_value)?.text?.toString() == value }
                matches
            }
            fun badge(compact: Boolean = false) = host.residentViews(compact).apply(context, null).findViewById<TextView>(R.id.shortcut_clean_badge)
            waitValue("456.7")
            scenario.onActivity {
                assertEquals("456.7MB", badge().text.toString())
                amount = 5678
                assertEquals("456.7MB", badge(true).text.toString())
                save(binding.root, "dirty")
                cleaning.completed("result-visit")
                assertEquals(View.GONE, badge().visibility)
            }
            waitValue("40.3")
            instrumentation.waitForIdleSync()
            SystemClock.sleep(80)
            scenario.onActivity {
                save(binding.root, "clean")
                now += 179_999
                assertEquals(View.GONE, badge().visibility)
                now++
                assertFalse(cleaning.state.value.dirty)
                assertEquals("567.8MB", badge().text.toString())
            }
            waitValue("567.8")
            scenario.onActivity {
                val value = binding.root.findViewById<TextView>(R.id.summary_value)
                val unit = binding.root.findViewById<TextView>(R.id.summary_unit)
                assertTrue("Value and unit must fit", unit.right <= (unit.parent as View).width)
                assertTrue(value.height >= value.layout.height + value.paddingTop + value.paddingBottom)
                renderer.dispose()
            }
        }
    }

    @Test fun enteringCompletionMarksMemoryOnceAcrossRecreation() {
        val app = context.applicationContext as CleanApplication
        instrumentation.runOnMainSync { app.homeCleaning.check() }
        val intent = CompletionContract.intent(context, CompletionReport(CompletionKind.CLEANUP, completed = 1, operationId = 12345), "SMART_CLEAN")
        ActivityScenario.launch<CompletionActivity>(intent).use { scenario ->
            var firstTime: Long? = null
            scenario.onActivity {
                firstTime = app.homeCleaning.state.value.lastCompletedAt
                assertNotNull(firstTime)
                assertFalse(app.homeCleaning.state.value.dirty)
            }
            SystemClock.sleep(40)
            scenario.recreate()
            scenario.onActivity {
                assertEquals(firstTime, app.homeCleaning.state.value.lastCompletedAt)
                assertFalse(app.homeCleaning.state.value.dirty)
            }
        }
    }

    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(context.getColor(R.color.home_background))
            view.draw(canvas)
            java.io.File(context.cacheDir, "home-cleaning-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(30)
        assertTrue("Home cleaning state timed out", condition())
    }
}
