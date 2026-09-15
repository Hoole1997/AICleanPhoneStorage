package com.example.aicleanphonestorage

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.analytics.EventSink
import com.example.aicleanphonestorage.core.analytics.MetricEvent
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 可控系统边界验证调用时机，不修改设备权限，不把假结果发到生产埋点，不测试视图。 */
@RunWith(AndroidJUnit4::class)
class PushPermissionTelemetryDeviceTest {
    @get:org.junit.Rule val noHotAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    private class Permissions : PushPermissionRequester {
        var granted = false
        var settings = false
        var grantAtApi = false
        var nativeCalls = 0
        lateinit var callback: (PushPermissionOutcome) -> Unit
        override fun isGranted() = granted
        override fun needsSettings(origin: PushPermissionRequest) = settings
        override fun request(origin: PushPermissionRequest, onStarted: () -> Unit, result: (PushPermissionOutcome) -> Unit) {
            if (grantAtApi) { granted = true; result(PushPermissionOutcome.ALREADY_ALLOWED); return }
            onStarted()
            nativeCalls++
            callback = result
        }
        fun finish(outcome: PushPermissionOutcome) { granted = outcome.granted; callback(outcome) }
    }

    @Test fun defaultGrantAndLateGrantNeverReportStart() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (position in PushPermissionPosition.entries) for (late in listOf(false, true)) {
                    val permissions = Permissions().apply { granted = !late; grantAtApi = late }
                    val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
                    val coordinator = PushPermissionCoordinator(activity, (activity.application as CleanApplication).notificationRuntime,
                        PushPermissionViewModel(SavedStateHandle()), position, { false }, {}, permissions,
                        events = EventSink { event, params -> events += event to params })
                    repeat(3) { coordinator.onResume(); coordinator.drain() }
                    assertEquals(0, permissions.nativeCalls)
                    if (android.os.Build.VERSION.SDK_INT <= 32) {
                        assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_RESULT to mapOf("Notific_Allow_Position" to position.wire, "Result" to "allow1")), events)
                    } else {
                        assertTrue("Android 13+ 已授权检查不能产生 allow1", events.isEmpty())
                    }
                }
            }
        }
    }

    @Test fun queuedRequestStartsOnlyAtApiAndReportsEachOutcomeOnce() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (position in PushPermissionPosition.entries) for (outcome in listOf(PushPermissionOutcome.ALLOWED, PushPermissionOutcome.DENIED, PushPermissionOutcome.DENIED_FOREVER)) {
                    val permissions = Permissions()
                    var otherPending = true
                    val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
                    val coordinator = PushPermissionCoordinator(activity, (activity.application as CleanApplication).notificationRuntime,
                        PushPermissionViewModel(SavedStateHandle()), position, { otherPending }, {}, permissions,
                        allowGuide = false, events = EventSink { event, params -> events += event to params })
                    coordinator.onResume(); coordinator.drain()
                    assertEquals(0, permissions.nativeCalls)
                    assertTrue(events.isEmpty())
                    otherPending = false
                    coordinator.drain()
                    assertEquals(1, permissions.nativeCalls)
                    assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_START), events.map { it.first })
                    permissions.finish(outcome); permissions.finish(outcome)
                    coordinator.onResume(); coordinator.drain()
                    assertEquals(1, permissions.nativeCalls)
                    assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_START, MetricEvent.NOTIFICATION_ALLOW_RESULT), events.map { it.first })
                    assertEquals(outcome.wire, events.last().second["Result"])
                    assertTrue(events.all { it.second["Notific_Allow_Position"] == position.wire })
                }
            }
        }
    }

    @Test fun inactiveHostAndUnlaunchedSettingsDoNotProduceStart() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val permissions = Permissions().apply { settings = true; granted = true }
                val model = PushPermissionViewModel(SavedStateHandle())
                val events = mutableListOf<Pair<MetricEvent, Map<String, Any>>>()
                val coordinator = PushPermissionCoordinator(activity, (activity.application as CleanApplication).notificationRuntime,
                    model, PushPermissionPosition.HOME, { false }, {}, permissions, allowGuide = false,
                    events = EventSink { event, params -> events += event to params })
                coordinator.drain() // 没有运行自动检查的宿主（例如热启动开屏）不能发默认允许事件。
                assertTrue(events.isEmpty())
                permissions.granted = false
                coordinator.onResume(); coordinator.drain()
                assertTrue(events.isEmpty())
                coordinator.onSettingsLaunched() // 模拟共享协调器真正调用系统设置 API 的边界。
                coordinator.onSettingsLaunched()
                assertEquals(listOf(MetricEvent.NOTIFICATION_ALLOW_START), events.map { it.first })
                permissions.granted = true
                coordinator.onSettingsResult(true)
                coordinator.onSettingsResult(true)
                assertEquals(2, events.size)
                assertEquals("allow", events.last().second["Result"])
            }
        }
    }
}
