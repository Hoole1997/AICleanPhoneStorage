package com.example.aicleanphonestorage

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.networktraffic.data.*
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficActivity
import com.example.aicleanphonestorage.feature.networktraffic.ui.NetworkTrafficViewModel
import com.example.aicleanphonestorage.feature.networktraffic.ui.TrafficStatus
import com.google.android.material.chip.Chip
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 只使用已有使用情况授权，不修改系统权限；覆盖真实包标志及真实时间窗口查询。 */
@RunWith(AndroidJUnit4::class)
class TrafficRepositoryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as CleanApplication).container

    @Test fun metadataExcludesAndroidAndRetainsTheUserInstalledApp() {
        val metadata = AppMetadataDataSource(context)
        assertTrue(metadata.applicationsForUid(android.os.Process.SYSTEM_UID).isEmpty())
        assertTrue(metadata.applicationsForUid(-1).isEmpty())
        val own = context.applicationInfo
        assertTrue(TrafficAppVisibility.isUserInstalled(own.flags))
        assertTrue(metadata.applicationsForUid(own.uid).any { it.packageName == context.packageName })
    }

    @Suppress("DEPRECATION")
    @Test fun everyPeriodReturnsOnlyManageableUserApps() = runBlocking {
        assumeTrue(UsageAccessChecker(context).isGranted())
        for (period in TrafficPeriod.entries) {
            val before = TrafficPeriodResolver().resolve(period)
            val snapshot = container.networkTrafficRepository.load(period) { }
            val after = TrafficPeriodResolver().resolve(period)
            assertEquals(period, snapshot.period)
            assertTrue(snapshot.window.startMillis in before.startMillis..after.startMillis)
            assertTrue(snapshot.window.endMillis in before.endMillis..after.endMillis)
            for (app in snapshot.apps) {
                assertTrue(TrafficAppVisibility.isApplicationUid(app.uid))
                assertTrue(app.packages.isNotEmpty())
                app.packages.forEach {
                    val flags = context.packageManager.getApplicationInfo(it.packageName, 0).flags
                    assertEquals(0, flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
                }
            }
        }
    }

    @Test fun weekChipLoadsWeekSnapshotWithoutOpeningLoadingDialog(): Unit = runBlocking {
        assumeTrue(UsageAccessChecker(context).isGranted())
        val snapshot = container.networkTrafficRepository.load(TrafficPeriod.THIS_MONTH) { }
        val token = container.trafficSnapshotTransfer.put(snapshot)
        ActivityScenario.launch<NetworkTrafficActivity>(Intent(context, NetworkTrafficActivity::class.java)
            .putExtra(NetworkTrafficActivity.EXTRA_SNAPSHOT_TOKEN, token)).use { scenario ->
            waitUntil {
                var ready = false
                scenario.onActivity { ready = it.hasWindowFocus() && it.findViewById<View>(R.id.period_week)?.isShown == true }
                ready
            }
            scenario.onActivity { it.findViewById<Chip>(R.id.period_week).performClick() }
            waitUntil {
                var ready = false
                scenario.onActivity {
                    val state = ViewModelProvider(it)[NetworkTrafficViewModel::class.java].state.value
                    ready = state.status == TrafficStatus.Ready && state.snapshot?.period == TrafficPeriod.THIS_WEEK &&
                        it.findViewById<Chip>(R.id.period_week).isChecked &&
                        it.findViewById<View>(R.id.traffic_refresh_indicator).visibility != View.VISIBLE &&
                        !it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.traffic_list).isAnimating
                }
                ready
            }
            scenario.onActivity { activity ->
                assertNull(activity.supportFragmentManager.findFragmentByTag("task_loading"))
                val root = activity.findViewById<View>(android.R.id.content)
                val image = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    val canvas = android.graphics.Canvas(image)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    root.draw(canvas)
                    val folder = File(context.getExternalFilesDir(null), "traffic-period-tests").apply { mkdirs() }
                    File(folder, "user_apps_week.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { image.recycle() }
            }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(25)
        assertTrue("Traffic page did not finish the selected period", condition())
    }
}
