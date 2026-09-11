package com.example.aicleanphonestorage

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.ui.loading.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryStateObserverDeviceTest {
    @Test fun resumedEntryReplaysProgressAndRemovesDialogWhenTaskEndedWhilePaused() {
        val frames = MutableStateFlow<Int?>(35)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var observer: Job
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var dialog: TaskLoadingDialogFragment
            scenario.onActivity { activity ->
                dialog = TaskLoadingDialogFragment.newInstance(LoadingUiState(13, "Scanning", "Preparing", 35, showAd = false))
                dialog.showNow(activity.supportFragmentManager, "entry-replay-test")
                observer = activity.observeEntryState(frames) { percent ->
                    if (percent == null) dialog.dismiss()
                    else dialog.render(LoadingUiState(13, "Scanning", "Preparing", percent, showAd = false))
                }
            }
            instrumentation.waitForIdleSync()
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { frames.value = 75 }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(35, dialog.requireView().findViewById<LinearProgressIndicator>(R.id.loading_progress).progress) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(75, dialog.requireView().findViewById<LinearProgressIndicator>(R.id.loading_progress).progress) }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { frames.value = null }
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag("entry-replay-test")) }
        }
        assertFalse(observer.isActive)
        assertTrue(observer.isCompleted)
    }
}
