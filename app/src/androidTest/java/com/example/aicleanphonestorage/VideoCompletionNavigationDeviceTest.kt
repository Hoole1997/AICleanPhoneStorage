package com.example.aicleanphonestorage

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.ui.completion.CompletionActivity
import com.example.aicleanphonestorage.core.ui.completion.CompletionContract
import com.example.aicleanphonestorage.feature.filecleaner.data.CleanupFeature
import com.example.aicleanphonestorage.feature.filecleaner.data.ScanHandle
import com.example.aicleanphonestorage.feature.filecleaner.operations.OperationSummary
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.testing.NoHotStartAdsRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 独立空索引只注入结果状态，验证真实 Activity Result/Continue 链路，不扫描或删除用户视频。 */
@RunWith(AndroidJUnit4::class)
class VideoCompletionNavigationDeviceTest {
    @get:Rule val noHotStartAds = NoHotStartAdsRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun continueClosesVideoFlowAndReturnsHome() = verifyContinue(recreateResult = false)
    @Test fun recreatedResultContinueStillReturnsHome() = verifyContinue(recreateResult = true)

    private fun verifyContinue(recreateResult: Boolean) {
        val index = (context.applicationContext as CleanApplication).container.fileScanRepository.index
        val scan = index.start(CleanupFeature.VIDEOS)
        index.finishScan(ScanHandle(scan, CleanupFeature.VIDEOS, 0, "Navigation test"))
        try {
            ActivityScenario.launch<FileCleanupActivity>(Intent(context, FileCleanupActivity::class.java)
                .putExtra(FileCleanupActivity.EXTRA_SCAN, scan)
                .putExtra(FileCleanupActivity.EXTRA_FEATURE, CleanupFeature.VIDEOS.name)).use { scenario ->
                waitUntil {
                    var ready = false
                    scenario.onActivity { ready = ViewModelProvider(it)[CleanupViewModel::class.java].state.value.handle != null }
                    ready
                }
                scenario.onActivity {
                    val model = ViewModelProvider(it)[CleanupViewModel::class.java]
                    // 测试专用状态注入，避免为导航回归触发媒体删除或增加生产测试入口。
                    val field = CleanupViewModel::class.java.getDeclaredField("current").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val state = field.get(model) as MutableStateFlow<CleanupUiState>
                    state.value = state.value.copy(operation = CleanupOperationState.Result(
                        scan, OperationSummary(0, 0, 0, 0, 0, 0)))
                }
                waitUntil { onMain { resumedResult() != null } }
                if (recreateResult) {
                    var previous: CompletionActivity? = null
                    instrumentation.runOnMainSync { previous = resumedResult(); previous!!.recreate() }
                    waitUntil { onMain { resumedResult()?.let { it !== previous } == true } }
                }
                instrumentation.runOnMainSync {
                    val result = requireNotNull(resumedResult())
                    assertEquals(CleanupFeature.VIDEOS.name, result.intent.getStringExtra(CompletionContract.SOURCE))
                    assertTrue(result.findViewById<View>(R.id.completion_continue).performClick())
                }
                waitUntil {
                    scenario.state == Lifecycle.State.DESTROYED && onMain {
                        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                        val homeVisible = listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED).any { stage ->
                            monitor.getActivitiesInStage(stage).any { it is MainActivity && !it.isFinishing }
                        }
                        homeVisible && resumedResult() == null
                    }
                }
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally {
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED).flatMap {
                    monitor.getActivitiesInStage(it).toList()
                }.filter { it is MainActivity || it is CompletionActivity || it is FileCleanupActivity }
                    .forEach { it.finish() }
            }
            index.discard(scan)
        }
    }

    private fun resumedResult(): CompletionActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<CompletionActivity>().firstOrNull()

    private fun onMain(block: () -> Boolean): Boolean {
        var value = false
        instrumentation.runOnMainSync { value = block() }
        return value
    }

    private fun waitUntil(block: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            if (block()) return
            SystemClock.sleep(50)
        }
        fail("Video completion did not reach the expected activity state")
    }
}
