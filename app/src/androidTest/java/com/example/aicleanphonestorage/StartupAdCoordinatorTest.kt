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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用假开屏请求验证回调和生命周期，不请求真实广告。About 仅作为原生 Activity 测试宿主。 */
@RunWith(AndroidJUnit4::class)
class StartupAdCoordinatorTest {
    @Test fun failedAdCallbackReleasesStartupAndBackgroundStopsLoopWithoutRequestingAgain() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            lateinit var complete: (Boolean) -> Unit
            var requests = 0
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                host = bind(activity) { position, call ->
                    assertEquals("startup_splash", position)
                    assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
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
                scenario.onActivity { host.ads.windowFocusChanged(it.hasWindowFocus()); shown = requests == 1 }
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

    @Test fun rotationKeepsPendingRequestAndCallbackUsesLatestNotification() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var host: Host
            lateinit var originalModel: StartupViewModel
            lateinit var complete: (Boolean) -> Unit
            var requests = 0
            scenario.onActivity { activity ->
                host = bind(activity) { _, call -> requests++; complete = call }
                originalModel = host.model
                host.model.accept(StartupEntry(NotificationDestination.PHOTOS))
            }
            waitUntil {
                var shown = false
                scenario.onActivity { host.ads.windowFocusChanged(it.hasWindowFocus()); shown = requests == 1 }
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

    private data class Host(val model: StartupViewModel, val ads: StartupAdCoordinator, val binding: ScreenStartupBinding)

    private fun bind(activity: AppCompatActivity, request: (String, (Boolean) -> Unit) -> Unit): Host {
        val model = ViewModelProvider(activity, viewModelFactory {
            initializer { StartupViewModel(SavedStateHandle()) {} }
        })[StartupViewModel::class.java]
        val binding = ScreenStartupBinding.inflate(activity.layoutInflater)
        activity.setContentView(binding.root)
        val renderer = StartupRenderer(binding)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = renderer.start()
            override fun onPause(owner: LifecycleOwner) = renderer.stop()
            override fun onDestroy(owner: LifecycleOwner) = renderer.dispose()
        })
        activity.lifecycleScope.launch { model.state.collect(renderer::render) }
        return Host(model, StartupAdCoordinator(activity, binding.root, model, request), binding)
    }

    private fun waitUntil(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
        assertTrue("Startup ad request timed out", check())
    }
}
