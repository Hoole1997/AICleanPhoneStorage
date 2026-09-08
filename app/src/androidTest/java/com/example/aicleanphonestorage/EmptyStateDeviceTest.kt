package com.example.aicleanphonestorage

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.ui.apps.AppIconLoader
import com.example.aicleanphonestorage.core.ui.empty.EmptyStateView
import com.example.aicleanphonestorage.databinding.ScreenFileCleanupBinding
import com.example.aicleanphonestorage.databinding.ScreenNetworkTrafficBinding
import com.example.aicleanphonestorage.databinding.ScreenNotificationCleanerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.AppManagerCatalog
import com.example.aicleanphonestorage.feature.appmanager.ui.AppManagerActivity
import com.example.aicleanphonestorage.feature.filecleaner.data.*
import com.example.aicleanphonestorage.feature.filecleaner.ui.*
import com.example.aicleanphonestorage.feature.junkcleaner.ui.JunkCleaningActivity
import com.example.aicleanphonestorage.feature.networktraffic.data.*
import com.example.aicleanphonestorage.feature.networktraffic.ui.*
import com.example.aicleanphonestorage.feature.notifications.data.NotificationCatalog
import com.example.aicleanphonestorage.feature.notifications.ui.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 空列表测试只写临时索引，不扫描/删除用户文件，不改系统权限。 */
@RunWith(AndroidJUnit4::class)
class EmptyStateDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val container
        get() = (context.applicationContext as CleanApplication).container

    private fun theme() = ContextThemeWrapper(context, R.style.Theme_AICleanPhoneStorage)

    private fun states(refresh: LoadState): CombinedLoadStates {
        val idle = LoadState.NotLoading(false)
        return CombinedLoadStates(refresh, idle, idle, LoadStates(refresh, idle, idle), null)
    }

    @Test
    fun pagingWaitsForPresentedPageAndNeverCallsFailureEmpty() {
        instrumentation.runOnMainSync {
            val binding = ScreenFileCleanupBinding.inflate(LayoutInflater.from(theme()))
            val renderer = CleanupListStateRenderer(binding)
            renderer.state(
                CleanupUiState(handle = ScanHandle(1, CleanupFeature.LARGE_FILES, 0, "test"))
            )
            renderer.loading(states(LoadState.NotLoading(false)), 0)
            assertFalse(binding.cleanupEmpty.isVisible)
            renderer.loading(states(LoadState.Loading), 0)
            renderer.pagesPresented(0)
            assertFalse(binding.cleanupEmpty.isVisible)
            renderer.loading(states(LoadState.NotLoading(true)), 0)
            assertTrue(binding.cleanupEmpty.isVisible)
            assertFalse(binding.cleanupFooter.isVisible)
            renderer.loading(states(LoadState.Loading), 0)
            assertFalse(binding.cleanupEmpty.isVisible)
            renderer.loading(states(LoadState.Error(IOException("unavailable"))), 0)
            assertFalse(binding.cleanupEmpty.isVisible)
            assertTrue(binding.cleanupError.isVisible)
            renderer.loading(states(LoadState.Loading), 0)
            renderer.pagesPresented(1)
            renderer.loading(states(LoadState.NotLoading(true)), 1)
            assertFalse(binding.cleanupEmpty.isVisible)
            assertFalse(binding.cleanupError.isVisible)
            assertTrue(binding.cleanupFiles.isVisible)
        }
    }

    @Test
    fun trafficUnavailableAndNotificationFailureUseErrorState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        try {
            instrumentation.runOnMainSync {
                val themed = theme()
                val traffic = ScreenNetworkTrafficBinding.inflate(LayoutInflater.from(themed))
                val renderer =
                    NetworkTrafficRenderer(
                        traffic,
                        scope,
                        AppIconLoader(themed, container.taskExecutor),
                        {},
                        {},
                        {},
                    )
                val snapshot =
                    TrafficSnapshot(
                        TrafficPeriod.THIS_MONTH,
                        TrafficWindow(0, 1),
                        NetworkUsage.unavailable(UsageAvailability.UNAVAILABLE),
                        NetworkUsage.unavailable(UsageAvailability.UNAVAILABLE),
                        emptyList(),
                    )
                renderer.render(
                    TrafficUiState(TrafficPeriod.THIS_MONTH, TrafficStatus.Ready, snapshot)
                )
                assertTrue(traffic.trafficStatusPanel.isVisible)
                assertFalse(traffic.trafficList.isVisible)
                renderer.dispose()
                val notifications =
                    ScreenNotificationCleanerBinding.inflate(LayoutInflater.from(themed))
                val notificationRenderer =
                    NotificationCleanerRenderer(
                        notifications,
                        scope,
                        AppIconLoader(themed, container.taskExecutor),
                        { _, _ -> },
                        {},
                        {},
                    )
                notificationRenderer.render(
                    NotificationUiState(NotificationPhase.Failed, NotificationCatalog(emptyList()))
                )
                assertTrue(notifications.notificationErrorPanel.isVisible)
                notificationRenderer.dispose()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun sharedArtworkMatchesDesignAndTextRemainsReachableInSmallViewport() {
        for ((widthDp, heightDp, scale) in listOf(Triple(375, 724, 1f), Triple(320, 190, 2f))) {
            var image: Bitmap? = null
            instrumentation.runOnMainSync {
                val configured =
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { fontScale = scale }
                    )
                val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
                val empty = EmptyStateView(themed)
                val density = themed.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                empty.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                empty.layout(0, 0, width, height)
                val message = empty.findViewById<TextView>(R.id.empty_state_message)
                assertEquals(themed.getString(R.string.list_empty_message), message.text.toString())
                assertTrue(message.height >= message.layout.height)
                if (heightDp < 220) assertTrue(empty.canScrollVertically(1))
                image =
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        Canvas(bitmap).apply {
                            drawColor(0xfff6f6f6.toInt())
                            empty.draw(this)
                        }
                    }
            }
            val directory =
                File(context.getExternalFilesDir(null), "empty-state-tests").apply { mkdirs() }
            image!!.let { bitmap ->
                File(directory, "empty_${widthDp}_${heightDp}_${scale}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    @Test
    fun actualFilePagesAndAppManagerShowEmptyAfterLoading() {
        assumeTrue(
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )
        val index = container.fileScanRepository.index
        for (feature in CleanupFeature.entries) {
            val id = index.start(feature)
            index.finishScan(ScanHandle(id, feature, 0, "Test fixtures"))
            try {
                ActivityScenario.launch<FileCleanupActivity>(
                        Intent(context, FileCleanupActivity::class.java)
                            .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                    )
                    .use { scenario ->
                        waitUntil {
                            var visible = false
                            scenario.onActivity {
                                visible = it.findViewById<View>(R.id.cleanup_empty).isVisible
                            }
                            visible
                        }
                        scenario.onActivity {
                            assertFalse(it.findViewById<View>(R.id.cleanup_footer).isVisible)
                            assertFalse(it.findViewById<View>(R.id.cleanup_error).isVisible)
                        }
                    }
                if (feature == CleanupFeature.SMART_CLEAN)
                    for (kind in
                        com.example.aicleanphonestorage.feature.junkcleaner.data.JunkKind.entries) {
                        ActivityScenario.launch<FileCleanupActivity>(
                                Intent(context, FileCleanupActivity::class.java)
                                    .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                                    .putExtra(FileCleanupActivity.EXTRA_BUCKET, kind.name)
                            )
                            .use { scenario ->
                                waitUntil {
                                    var visible = false
                                    scenario.onActivity {
                                        visible =
                                            it.findViewById<View>(R.id.cleanup_empty).isVisible
                                    }
                                    visible
                                }
                                scenario.onActivity { activity ->
                                    assertFalse(
                                        "Detail action must be hidden: $kind",
                                        activity.findViewById<View>(R.id.cleanup_footer).isVisible,
                                    )
                                    assertFalse(
                                        "Empty detail must not show selection: $kind",
                                        activity
                                            .findViewById<View>(R.id.cleanup_select_all)
                                            .isVisible,
                                    )
                                    assertEquals(
                                        context.getString(R.string.list_empty_message),
                                        activity
                                            .findViewById<TextView>(R.id.empty_state_message)
                                            .text
                                            .toString(),
                                    )
                                }
                            }
                    }
                if (feature == CleanupFeature.SMART_CLEAN)
                    ActivityScenario.launch<JunkCleaningActivity>(
                            Intent(context, JunkCleaningActivity::class.java)
                                .putExtra(FileCleanupActivity.EXTRA_SCAN, id)
                        )
                        .use { scenario ->
                            waitUntil {
                                var visible = false
                                scenario.onActivity {
                                    visible = it.findViewById<View>(R.id.junk_empty).isVisible
                                }
                                visible
                            }
                            scenario.onActivity {
                                assertFalse(it.findViewById<View>(R.id.junk_clean).isVisible)
                            }
                        }
            } finally {
                index.discard(id)
            }
        }
        val token = container.appManagerTransfer.put(AppManagerCatalog(emptyList()))
        ActivityScenario.launch<AppManagerActivity>(
                Intent(context, AppManagerActivity::class.java)
                    .putExtra(AppManagerActivity.EXTRA_CATALOG, token)
            )
            .use { scenario ->
                waitUntil {
                    var visible = false
                    scenario.onActivity {
                        visible = it.findViewById<View>(R.id.app_manager_empty).isVisible
                    }
                    visible
                }
            }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40)
        assertTrue("Empty state did not settle", condition())
    }
}
