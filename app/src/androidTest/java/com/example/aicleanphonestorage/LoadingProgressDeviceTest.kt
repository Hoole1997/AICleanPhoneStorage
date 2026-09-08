package com.example.aicleanphonestorage

import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.ui.loading.*
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadingProgressDeviceTest {
    @Test
    fun fileLoadingIsDeterminateFromFirstFrameAcrossScanStages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var now = 0L
                val timeline = ContinuousEntryProgress(0, 2500, { now }, listOf("FILES", "PHOTOS"))
                val dialog =
                    TaskLoadingDialogFragment.newInstance(
                        LoadingUiState(70, "Scanning…", "Preparing", 0)
                    )
                dialog.showNow(activity.supportFragmentManager, "loading.progress.test")
                try {
                    val indicator =
                        dialog
                            .requireView()
                            .findViewById<LinearProgressIndicator>(R.id.loading_progress)
                    assertFalse(indicator.isIndeterminate)
                    assertEquals(0, indicator.progress)
                    var previous = 0
                    fun render() {
                        val frame = timeline.frame()
                        dialog.render(
                            LoadingUiState(70, "Scanning…", frame.detail.stage, frame.percent)
                        )
                        assertFalse(indicator.isIndeterminate)
                        assertTrue(indicator.progress >= previous)
                        previous = indicator.progress
                    }
                    now = 1200
                    timeline.report(TaskProgress("FILES", 50))
                    render()
                    now = 2200
                    timeline.report(TaskProgress("FILES", 100, 100))
                    render()
                    now = 2600
                    timeline.report(TaskProgress("PHOTOS", 0))
                    render()
                    now = 3000
                    timeline.report(TaskProgress("PHOTOS", 100))
                    render()
                    timeline.complete(100, "FILES")
                    render()
                    now += 400
                    render()
                    assertEquals(100, indicator.progress)
                } finally {
                    dialog.dismissNow()
                }
            }
        }
    }
}
