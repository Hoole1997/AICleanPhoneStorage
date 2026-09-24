package com.example.aicleanphonestorage

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.InterstitialActions
import com.example.aicleanphonestorage.core.permissions.PermissionCoordinator
import com.example.aicleanphonestorage.core.permissions.PermissionFlowViewModel
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.data.ScanHandle
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 用共享广告状态及可控回调复现回首页后再次扫描；只使用空测试索引，不读取用户媒体。 */
@RunWith(AndroidJUnit4::class)
class VideoReentryDeviceTest {
    @get:Rule val noHotAds = NoHotStartAdsRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container = (instrumentation.targetContext.applicationContext as CleanApplication).container

    @Test fun completedScanResumesWhenHomeExitAdReleasesWithoutLifecycleChange() = verify(paused = false)
    @Test fun completedScanWaitsUntilHostResumesAndDoesNotNavigateTwice() = verify(paused = true)

    @Test fun cancelledEntryIsNotReopenedWhenTheQueueBecomesAvailable() = verify(paused = false, cancelled = true)

    private fun verify(paused: Boolean, cancelled: Boolean = false) {
        val index = container.fileScanRepository.index
        val id = index.start(CleanupFeature.VIDEOS)
        val handle = ScanHandle(id, CleanupFeature.VIDEOS, 0, "Reentry test")
        index.finishScan(handle)
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var entry: CleanupEntryViewModel
                lateinit var coordinator: CleanupEntryCoordinator
                lateinit var finishExit: (Boolean) -> Unit
                lateinit var finishScan: (Boolean) -> Unit
                var scanRequests = 0
                var exitCompletions = 0
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { activity ->
                    entry = CleanupEntryViewModel(container.fileScanRepository, SavedStateHandle())
                    val permissions = PermissionCoordinator(activity,
                        PermissionFlowViewModel(container.permissionAccess, SavedStateHandle()), container.permissionAccess)
                    // 与首页一致：不同协调器拥有独立处理器，但共用 Activity 级广告队列。
                    val exitAds = InterstitialActions(activity) { _, callback -> finishExit = callback }
                    exitAds.register("test.home.exit") { exitCompletions++ }
                    val entryAds = InterstitialActions(activity) { position, callback ->
                        assertEquals("scan_complete_video", position)
                        scanRequests++
                        finishScan = callback
                    }
                    coordinator = CleanupEntryCoordinator(activity, entry, permissions, entryAds)
                    exitAds.run("test.home.exit", "back_home_video")
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity {
                    val field = CleanupEntryViewModel::class.java.getDeclaredField("current").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val state = field.get(entry) as MutableStateFlow<CleanupEntryState>
                    state.value = CleanupEntryState.Ready(handle)
                    coordinator.render(state.value)
                    assertEquals(0, scanRequests)
                    assertEquals(CleanupEntryState.Ready(handle), entry.state.value)
                }
                if (cancelled) scenario.onActivity { entry.cancel() }
                if (paused) scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { finishExit(false) }
                if (paused) {
                    scenario.onActivity {
                        assertEquals(0, scanRequests)
                        assertEquals(0, exitCompletions)
                    }
                    scenario.moveToState(Lifecycle.State.RESUMED)
                }
                if (cancelled) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertEquals(1, exitCompletions)
                        assertEquals(0, scanRequests)
                        assertEquals(CleanupEntryState.Idle, entry.state.value)
                    }
                    return@use
                }
                // 关键回归：不手动再次调用 render，也不要求用户点第三次或切后台。
                waitUntil {
                    var requested = false
                    scenario.onActivity { requested = scanRequests == 1 }
                    requested
                }
                scenario.onActivity {
                    assertEquals(1, exitCompletions)
                    assertEquals(CleanupEntryState.Idle, entry.state.value)
                    finishScan(false)
                    finishScan(false) // SDK 重复回调不能打开两份视频页。
                }
                waitUntil {
                    var opened = false
                    instrumentation.runOnMainSync {
                        val pages = ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED).filterIsInstance<FileCleanupActivity>()
                        opened = pages.singleOrNull()?.intent?.getLongExtra(FileCleanupActivity.EXTRA_SCAN, -1) == id
                    }
                    opened
                }
                instrumentation.runOnMainSync {
                    val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                    val pages = listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED)
                        .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<FileCleanupActivity>()
                        .filter { !it.isFinishing }
                    assertEquals(1, pages.size)
                    pages.single().finish()
                    entry.cancel()
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED)
                    .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList() }
                    .filterIsInstance<FileCleanupActivity>().forEach { it.finish() }
            }
            index.discard(id)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(40)
        }
        fail("Completed video scan did not resume navigation after the advertising queue became available")
    }
}
