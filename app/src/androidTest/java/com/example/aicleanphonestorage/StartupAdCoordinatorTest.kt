package com.example.aicleanphonestorage

import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.startup.*
import io.docview.push.NotificationDestination
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用假开屏请求验证回调和生命周期，不请求真实广告。About 仅作为原生 Activity 测试宿主。 */
@RunWith(AndroidJUnit4::class)
class StartupAdCoordinatorTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()

    @Test fun offlineDeadlineCancelsRequestBeforeReleasingRouteAndLateCallbackIsIgnored() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            lateinit var complete: (Boolean) -> Unit
            lateinit var loaded: (Boolean) -> Unit
            var cancelled = 0
            scenario.onActivity { activity ->
                host = bind(activity, network = kotlinx.coroutines.flow.MutableStateFlow(StartupNetworkState.OFFLINE),
                    onRequestLoaded = { loaded = it },
                    cancel = {
                        cancelled++
                        try { loaded(true); fail("Expired request reached ad display") }
                        catch (_: kotlinx.coroutines.CancellationException) { }
                        complete(false)
                        assertFalse(host.model.state.value.ready)
                    }) { _, callback -> complete = callback }
                host.model.accept(StartupEntry(NotificationDestination.NETWORK))
            }
            waitUntil {
                var ready = false
                scenario.onActivity { host.ads.windowFocusChanged(it.hasWindowFocus()); ready = host.model.state.value.ready }
                ready
            }
            scenario.onActivity {
                assertEquals(1, cancelled)
                complete(true)
                assertEquals(NotificationDestination.NETWORK, host.model.consume()?.destination)
                assertNull(host.model.consume())
            }
        }
    }

    @Test fun networkObservationStopsWithPageAndResubscribesOnlyOnResume() {
        val subscriptions = java.util.concurrent.atomic.AtomicInteger()
        val network = kotlinx.coroutines.flow.callbackFlow {
            subscriptions.incrementAndGet()
            trySend(StartupNetworkState.ONLINE)
            awaitClose { subscriptions.decrementAndGet() }
        }
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { bind(it, network = network) { _, _ -> } }
            waitUntil { subscriptions.get() == 1 }
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil { subscriptions.get() == 0 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil { subscriptions.get() == 1 }
        }
        waitUntil { subscriptions.get() == 0 }
    }
    @Test
    fun failedAdCallbackReleasesStartupAndBackgroundStopsLoopWithoutRequestingAgain() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            lateinit var complete: (Boolean) -> Unit
            var requests = 0
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                host =
                    bind(activity) { position, call ->
                        assertEquals("splash", position)
                        assertTrue(
                            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        )
                        assertTrue(activity.hasWindowFocus())
                        requests++
                        complete = call
                    }
                host.model.accept(StartupEntry(NotificationDestination.CLEAN))
                assertEquals(0, requests)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil {
                var shown = false
                scenario.onActivity {
                    host.ads.windowFocusChanged(it.hasWindowFocus())
                    shown = requests == 1
                }
                shown
            }
            scenario.onActivity {
                assertTrue(host.binding.startupProgress.isIndeterminate)
                assertEquals(View.VISIBLE, host.binding.startupProgress.visibility)
                assertFalse(host.model.state.value.ready)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertEquals(View.INVISIBLE, host.binding.startupProgress.visibility)
                assertEquals(View.VISIBLE, host.binding.startupProgressStatic.visibility)
                complete(false)
                complete(true)
                assertTrue(host.model.state.value.adCompleted)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil {
                var ready = false
                scenario.onActivity { ready = host.model.state.value.ready }
                ready
            }
            scenario.onActivity {
                assertEquals(1, requests)
                assertEquals(NotificationDestination.CLEAN, host.model.consume()?.destination)
                assertNull(host.model.consume())
            }
        }
    }

    @Test
    fun rotationKeepsPendingRequestAndCallbackUsesLatestNotification() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            lateinit var originalModel: StartupViewModel
            lateinit var complete: (Boolean) -> Unit
            var requests = 0
            scenario.onActivity { activity ->
                host =
                    bind(activity) { _, call ->
                        requests++
                        complete = call
                    }
                originalModel = host.model
                host.model.accept(StartupEntry(NotificationDestination.PHOTOS))
            }
            waitUntil {
                var shown = false
                scenario.onActivity {
                    host.ads.windowFocusChanged(it.hasWindowFocus())
                    shown = requests == 1
                }
                shown
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                host = bind(activity) { _, _ -> requests++ }
                assertSame(originalModel, host.model)
                host.model.accept(StartupEntry(NotificationDestination.SCREENSHOTS))
                host.ads.windowFocusChanged(activity.hasWindowFocus())
                assertEquals(1, requests)
                complete(true)
                complete(false)
            }
            waitUntil {
                var ready = false
                scenario.onActivity { ready = host.model.state.value.ready }
                ready
            }
            scenario.onActivity {
                assertEquals(NotificationDestination.SCREENSHOTS, host.model.consume()?.destination)
                assertNull(host.model.consume())
            }
        }
    }

    @Test
    fun splashRequestWaitsForPermissionFlowCompletion() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            var requests = 0
            scenario.onActivity { activity ->
                host = bind(activity, permissionsComplete = false) { _, _ -> requests++ }
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .waitForIdleSync()
            scenario.onActivity { activity ->
                host.ads.windowFocusChanged(activity.hasWindowFocus())
                assertTrue(host.model.state.value.prepared)
                assertFalse(host.model.state.value.permissionCompleted)
                assertEquals(0, requests)
                host.model.permissionFinished()
            }
            waitUntil {
                scenario.onActivity { host.ads.windowFocusChanged(it.hasWindowFocus()) }
                requests == 1
            }
            scenario.onActivity {
                host.model.permissionFinished()
                assertNull(host.model.beginAd())
                assertEquals(1, requests)
            }
        }
    }

    private data class Host(
        val model: StartupViewModel,
        val ads: StartupAdCoordinator,
        val binding: ScreenStartupBinding,
    )

    private fun bind(
        activity: AppCompatActivity,
        permissionsComplete: Boolean = true,
        network: kotlinx.coroutines.flow.Flow<StartupNetworkState> = kotlinx.coroutines.flow.MutableStateFlow(StartupNetworkState.ONLINE),
        cancel: () -> Unit = {},
        onRequestLoaded: ((Boolean) -> Unit) -> Unit = {},
        request: (String, (Boolean) -> Unit) -> Unit,
    ): Host {
        val model =
            ViewModelProvider(
                activity,
                viewModelFactory {
                    initializer {
                        StartupViewModel(SavedStateHandle()) {}
                            .also { if (permissionsComplete) it.permissionFinished() }
                    }
                },
            )[StartupViewModel::class.java]
        val binding = ScreenStartupBinding.inflate(activity.layoutInflater)
        activity.setContentView(binding.root)
        val renderer = StartupRenderer(binding)
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) = renderer.start()

                override fun onPause(owner: LifecycleOwner) = renderer.stop()

                override fun onDestroy(owner: LifecycleOwner) = renderer.dispose()
            }
        )
        activity.lifecycleScope.launch { model.state.collect(renderer::render) }
        return Host(model, StartupAdCoordinator(activity, binding.root, model,
            request = { position, loaded, done -> onRequestLoaded(loaded); request(position, done); cancel }, network = network), binding)
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue("Startup ad request timed out", check())
    }
}
