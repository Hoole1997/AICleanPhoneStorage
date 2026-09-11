package com.example.aicleanphonestorage

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.databinding.ScreenStartupBinding
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import com.example.aicleanphonestorage.feature.startup.*
import io.docview.push.NotificationDestination
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** About 只承载启动页预览，调用生产导航入口验证窗口转场；不请求或点击真实广告。 */
@RunWith(AndroidJUnit4::class)
class StartupTransitionDeviceTest {
    @Test fun scopedFadeShowsHomeAndRemovesSourceActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val artwork = context.resources.getDrawable(R.drawable.startup_background, null)
        val directory = File(context.getExternalFilesDir(null), "startup-fade").apply { mkdirs() }
        val times = mutableListOf<Long>()
        var renderer: StartupRenderer? = null
        val captured = mutableListOf<Bitmap>()
        fun capture() {
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            times += SystemClock.elapsedRealtime()
            // 限定预览采样最多 19 张、每张不超过 320×640；转场结束后再编码，避免 PNG 编码漏掉中间帧。
            val scale = minOf(320f / screenshot.width, 640f / screenshot.height, 1f)
            val small = Bitmap.createScaledBitmap(screenshot, (screenshot.width * scale).toInt(), (screenshot.height * scale).toInt(), true)
            if (small !== screenshot) screenshot.recycle()
            captured += small
        }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val binding = ScreenStartupBinding.inflate(activity.layoutInflater)
                    binding.startupBackground.setImageDrawable(artwork)
                    activity.setContentView(binding.root)
                    renderer = StartupRenderer(binding).also {
                        it.render(StartupState(prepared = true, permissionCompleted = true, adCompleted = true, minimumStayComplete = true))
                    }
                }
                // 等待测试宿主的系统启动窗口退出，避免把前一段系统转场混入采样。
                SystemClock.sleep(400)
                capture()
                scenario.onActivity { StartupNavigation.openHome(it, StartupEntry(NotificationDestination.HOME), animate = true) }
                repeat(18) { capture() }
                val end = SystemClock.elapsedRealtime() + 8000
                var home = false
                while (!home && SystemClock.elapsedRealtime() < end) {
                    instrumentation.runOnMainSync {
                        home = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it is MainActivity && !it.isFinishing }
                    }
                    if (!home) SystemClock.sleep(30)
                }
                assertTrue(home)
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally {
            instrumentation.runOnMainSync {
                renderer?.dispose()
                listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED).forEach { stage ->
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                        .filterIsInstance<MainActivity>().forEach { it.finish() }
                }
            }
            captured.forEachIndexed { index, bitmap ->
                try {
                    File(directory, "frame_%02d.png".format(index)).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { bitmap.recycle() }
            }
            File(directory, "times.txt").writeText(times.joinToString("\n"))
        }
    }
}
