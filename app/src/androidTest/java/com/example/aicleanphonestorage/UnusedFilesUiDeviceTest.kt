package com.example.aicleanphonestorage

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.FileCleanupActivity
import com.example.aicleanphonestorage.feature.unused.data.UnusedKind
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 验证实际分组导航、共享选择与返回刷新；样例只存在于索引，不执行删除。 */
@RunWith(AndroidJUnit4::class)
class UnusedFilesUiDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    @Test fun groupDetailSelectionReturnsToOverviewWithUpdatedTotals() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val index = (context.applicationContext as CleanApplication).container.fileScanRepository.index
        val scan = index.start(CleanupFeature.UNUSED_FILES)
        try {
            index.insert(scan, UnusedKind.entries.mapIndexed { i, kind -> ScannedFile(
                uri = "file:///unused_ui_$scan/$i", name = "sample_$i.pdf", mime = "application/pdf", size = 1_000_000,
                modifiedMillis = 1, category = FileCategory.DOCUMENTS, backend = FileBackend.DIRECT,
                scope = "/unused_ui_$scan", bucket = kind.bucket,
            ) })
            val handle = ScanHandle(scan, CleanupFeature.UNUSED_FILES, 3, "Test fixtures").also(index::finishScan)
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, scan)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.UNUSED_FILES.name)).use { scenario ->
                waitUntil { var enabled = false; scenario.onActivity { enabled = it.findViewById<View>(R.id.cleanup_action).isEnabled }; enabled }
                onView(withText(R.string.unused_installed_apk)).check(matches(isDisplayed()))
                onView(withText(R.string.unused_residue)).check(matches(isDisplayed()))
                onView(withText(R.string.unused_download)).perform(click())
                onView(withId(R.id.cleanup_title)).check(matches(withText(R.string.unused_download)))
                waitUntil {
                    var enabled = false
                    instrumentation.runOnMainSync {
                        enabled = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .any { it.findViewById<View>(R.id.cleanup_select_all)?.isEnabled == true }
                    }
                    enabled
                }
                onView(withId(R.id.cleanup_select_all)).check(matches(withText(R.string.cleanup_deselect_all)))
                onView(withId(R.id.cleanup_select_all)).perform(click())
                waitUntil { index.totals(handle, CleanupFilter(bucket = UnusedKind.DOWNLOAD.bucket)).selectedCount == 0 }
                pressBack()
                waitUntil {
                    var updated = false
                    scenario.onActivity { updated = it.findViewById<android.widget.TextView>(R.id.cleanup_action).text.contains("2") }
                    updated
                }
                assertEquals(2, index.totals(handle, CleanupFilter()).selectedCount)
                onView(withId(R.id.cleanup_action)).check(matches(isEnabled()))
                val image = instrumentation.uiAutomation.takeScreenshot()
                try {
                    val folder = File(context.getExternalFilesDir(null), "unused-tests").apply { mkdirs() }
                    File(folder, "groups.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { image.recycle() }
            }
        } finally { index.discard(scan) }
    }
    private fun waitUntil(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!check()) { check(SystemClock.uptimeMillis() < end) { "UI did not settle" }; SystemClock.sleep(40) }
    }
}
