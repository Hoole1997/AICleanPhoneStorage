package com.example.aicleanphonestorage

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.app.ad.HotStartAdCoordinator
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.startup.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 注入限频快照和广告回调，不请求/点击真实广告。真实进程监听在测试期间由交接 guard 屏蔽。 */
@RunWith(AndroidJUnit4::class)
class HotStartAdDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as CleanApplication
    private class Process : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() { registry.currentState = Lifecycle.State.STARTED }
        fun stop() { registry.currentState = Lifecycle.State.CREATED }
    }

    @Test fun realProcessForegroundEventTriggersOnceAndRotationDoesNotTrigger() = guarded {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var coordinator: HotStartAdCoordinator
            var launches = 0
            scenario.onActivity {
                coordinator = HotStartAdCoordinator(app, eligible = { true }, blocked = { false }, open = { launches++ })
            }
            try {
                scenario.recreate()
                scenario.onActivity { assertEquals(0, launches) }
                scenario.moveToState(Lifecycle.State.CREATED)
                waitUntil {
                    var stopped = false
                    instrumentation.runOnMainSync { stopped = ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.CREATED }
                    stopped
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitUntil { launches == 1 }
                scenario.onActivity { activity ->
                    coordinator.onActivityResumed(activity)
                    assertEquals(1, launches)
                }
            } finally { instrumentation.runOnMainSync { coordinator.close() } }
        }
    }

    @Test fun guardedDepartureStaysSkippedAfterGuardReleasedBeforeReturn() = guarded {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var coordinator: HotStartAdCoordinator
            var blocked = true
            var checks = 0
            scenario.onActivity { activity ->
                val process = Process()
                coordinator = HotStartAdCoordinator(app, process.lifecycle, eligible = { checks++; true }, blocked = { blocked }, open = {})
                process.start()
                coordinator.onActivityResumed(activity)
                coordinator.onActivityPaused(activity)
                process.stop()
                blocked = false
                process.start()
                coordinator.onActivityResumed(activity)
            }
            try {
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(0, checks) }
            } finally { instrumentation.runOnMainSync { coordinator.close() } }
        }
    }

    @Test fun blockedFrequencyDoesNotLaunchAndStaleCheckCannotLaunchAfterPause() = guarded {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var coordinator: HotStartAdCoordinator
            lateinit var process: Process
            var checks = 0
            var launches = 0
            var answer = CompletableDeferred<Boolean>()
            scenario.onActivity { activity ->
                process = Process()
                coordinator = HotStartAdCoordinator(app, process.lifecycle, eligible = {
                    checks++
                    answer.await()
                }, blocked = { false }, open = { launches++ })
                process.start()
                coordinator.onActivityResumed(activity)
                assertEquals(0, checks)
                coordinator.onActivityPaused(activity)
                process.stop(); process.start()
                coordinator.onActivityResumed(activity)
            }
            waitUntil { checks == 1 }
            instrumentation.runOnMainSync { answer.complete(false) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0, launches)
                answer = CompletableDeferred()
                coordinator.onActivityPaused(activity)
                process.stop(); process.start()
                coordinator.onActivityResumed(activity)
            }
            waitUntil { checks == 2 }
            scenario.onActivity { activity ->
                coordinator.onActivityPaused(activity)
                answer.complete(true)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(0, launches); coordinator.close() }
        }
    }

    @Test fun hotSplashCallbackReturnsToSameActivityWithoutOpeningHomeOrRepeating() = guarded {
        var startups = 0
        var homes = 0
        var requests = 0
        var callback: (() -> Unit)? = null
        var injectionFailure: String? = null
        // 在窗口首帧之前占用 ViewModel 请求编号，使正式协调器不再请求 SDK。
        val trace = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is StartupActivity) {
                    startups++
                    activity.lifecycleScope.launch {
                        val model = ViewModelProvider(activity)[StartupViewModel::class.java]
                        model.state.first { it.prepared && it.permissionCompleted }
                        val id = model.beginAd()
                        if (id == null) injectionFailure = "Expected fake request before first draw"
                        else { requests++; callback = { model.adFinished(id) } }
                    }
                }
                if (activity is com.example.aicleanphonestorage.app.MainActivity) homes++
            }
            override fun onActivityCreated(a: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        }
        instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(trace) }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                lateinit var coordinator: HotStartAdCoordinator
                lateinit var process: Process
                lateinit var original: Activity
                scenario.onActivity { activity ->
                    original = activity
                    process = Process()
                    coordinator = HotStartAdCoordinator(app, process.lifecycle, eligible = { true }, blocked = { false })
                    process.start()
                    coordinator.onActivityResumed(activity)
                    coordinator.onActivityPaused(activity)
                    process.stop(); process.start()
                    coordinator.onActivityResumed(activity)
                }
                try {
                    waitUntil { callback != null || injectionFailure != null }
                    assertNull(injectionFailure)
                    waitUntil {
                        var focused = false
                        instrumentation.runOnMainSync {
                            focused = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                                .any { it is StartupActivity && it.hasWindowFocus() }
                        }
                        focused
                    }
                    snapshot("splash")
                    instrumentation.runOnMainSync { callback!!.invoke() }
                    waitUntil { scenario.state == Lifecycle.State.RESUMED }
                    scenario.onActivity { activity ->
                        assertSame(original, activity)
                        assertEquals(1, startups)
                        assertEquals(1, requests)
                        assertEquals(0, homes)
                    }
                    // 系统窗口淡出可能持续约 500ms；完成后再留存稳定页面截图。
                    SystemClock.sleep(1_000)
                    snapshot("returned")
                } finally { instrumentation.runOnMainSync { coordinator.close() } }
            }
        } finally { instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(trace) } }
    }

    private fun snapshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            java.io.File(instrumentation.targetContext.cacheDir, "hot-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun guarded(block: () -> Unit) {
        lateinit var guard: AutoCloseable
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold() }
        try { block() } finally { instrumentation.runOnMainSync { guard.close() } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue("Hot startup timed out", condition())
    }
}
