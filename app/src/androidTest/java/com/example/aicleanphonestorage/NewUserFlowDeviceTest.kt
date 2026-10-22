package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.LocaleList
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.core.lifecycle.ForegroundTransitionGuard
import com.example.aicleanphonestorage.databinding.ScreenDeviceScanBinding
import com.example.aicleanphonestorage.feature.onboarding.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NewUserFlowDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val output get() = File(context.getExternalFilesDir(null), "new-user-verification").apply { mkdirs() }

    @Test fun diskStateRetriesBeforeInterstitialButNeverAfterItOrHome() = runBlocking {
        val file = File(context.cacheDir, "new-user-${System.nanoTime()}.preferences_pb")
        suspend fun session(legacy: Boolean, action: suspend (PersistentNewUserFlowStore) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val data = PreferenceDataStoreFactory.create(scope = scope) { file }
            try { action(PersistentNewUserFlowStore(data) { legacy }) }
            finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        try {
            session(false) { assertTrue(it.eligible()) }
            // 模拟设备扫描初始化了数据库，随后进程死亡；pending 必须优先于迁移检测。
            session(true) { assertTrue(it.eligible()); it.interstitialStarted() }
            session(false) { assertFalse(it.eligible()); it.homeReached() }
            session(false) { assertFalse(it.eligible()) }
        } finally { file.delete() }
        val legacyFile = File(context.cacheDir, "new-user-legacy-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) { legacyFile }
            assertFalse(PersistentNewUserFlowStore(data) { true }.eligible())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); legacyFile.delete() }
    }

    @Test fun deviceReadsRealValuesAndIndependentActivitySurvivesRecreation() {
        // 息屏时生产页面应暂停；此用例验证可见窗口，不修改测试设备的锁屏/唤醒设置。
        org.junit.Assume.assumeTrue(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        org.junit.Assume.assumeFalse(context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        var guard: AutoCloseable? = null
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold("new-user-device-test") }
        try {
            ActivityScenario.launch(DeviceScanActivity::class.java).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 15_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { ready = it.findViewById<View>(R.id.scanAction).isEnabled }
                    if (!ready) SystemClock.sleep(40)
                }
                var diagnostic = ""
                scenario.onActivity {
                    val model = androidx.lifecycle.ViewModelProvider(it)[DeviceScanViewModel::class.java]
                    diagnostic = "state=${model.state.value}; restored=${model.processRestored}; lifecycle=${it.lifecycle.currentState}; focus=${it.hasWindowFocus()}"
                }
                assertTrue("Real device scan completes without storage permission: $diagnostic", ready)
                scenario.onActivity { activity ->
                    val binding = ScreenDeviceScanBinding.bind(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    assertTrue(binding.deviceRow.itemValue.text.contains(android.os.Build.MODEL))
                    assertTrue(binding.systemRow.itemValue.text.contains(android.os.Build.VERSION.RELEASE))
                    assertEquals(context.getString(R.string.new_user_done), binding.engineRow.itemValue.text.toString())
                    assertEquals(View.VISIBLE, binding.completedCheck.visibility)
                    binding.scanScroll.fullScroll(View.FOCUS_DOWN)
                }
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try { File(output, "real-device-complete.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.scanAction).isEnabled) }
            }
        } finally { instrumentation.runOnMainSync { guard?.close() } }
    }

    @Test fun allLanguagesFitAtNormalAndLargeFontAndSmallViewport() {
        val locales = listOf("en", "zh-Hans", "de", "es", "fr", "pt", "ru", "ja", "ko", "id", "vi", "tr", "hi", "bn", "ar", "ur")
        val values = listOf(DeviceInfoValue.Text("samsung SM-G986N"), DeviceInfoValue.Text("Android 13"),
            DeviceInfoValue.Text("1080 × 2400"), DeviceInfoValue.Text("450 dpi"),
            DeviceInfoValue.Storage(72_600_000_000, 256_000_000_000), DeviceInfoValue.Done)
        val complete = DeviceScanState(values = values)
        for (tag in locales) for (scale in listOf(1f, 2f)) {
            renderAndCheck(tag, scale, if (scale == 1f) 375 else 320, if (scale == 1f) 948 else 568, complete, "complete")
        }
        renderAndCheck("en", 1f, 375, 948, DeviceScanState(values = values.dropLast(1) + null), "scanning")
        renderAndCheck("en", 2f, 320, 568, DeviceScanState(values = values.dropLast(1) + null, engineFailed = true), "failed")
    }

    @Test fun motionStopsOnFocusLossBackgroundOffscreenAndCompletion() {
        org.junit.Assume.assumeTrue(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        var guard: AutoCloseable? = null
        instrumentation.runOnMainSync { guard = ForegroundTransitionGuard.hold("new-user-motion-test") }
        try {
            ActivityScenario.launch(com.example.aicleanphonestorage.feature.settings.AboutActivity::class.java).use { scenario ->
                lateinit var binding: ScreenDeviceScanBinding
                lateinit var renderer: DeviceScanRenderer
                scenario.onActivity {
                    binding = ScreenDeviceScanBinding.inflate(it.layoutInflater)
                    it.setContentView(binding.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        (400 * it.resources.displayMetrics.density).toInt()))
                    renderer = DeviceScanRenderer(binding)
                    renderer.render(DeviceScanState())
                }
                instrumentation.waitForIdleSync()
                try {
                    scenario.onActivity { renderer.resumed(true); renderer.windowFocus(it.hasWindowFocus()) }
                    val deadline = SystemClock.elapsedRealtime() + 3_000
                    var moving = false
                    while (!moving && SystemClock.elapsedRealtime() < deadline) {
                        scenario.onActivity { renderer.windowFocus(it.hasWindowFocus()); moving = binding.scanningRing.rotation > 0f }
                        if (!moving) SystemClock.sleep(30)
                    }
                    assertTrue("Visible scan ring rotates", moving)
                    scenario.onActivity { renderer.windowFocus(false) }
                    SystemClock.sleep(80)
                    scenario.onActivity { assertEquals(0f, binding.scanningRing.rotation) }
                    scenario.onActivity { renderer.windowFocus(true); renderer.resumed(false) }
                    SystemClock.sleep(80)
                    scenario.onActivity { assertEquals(0f, binding.scanningRing.rotation) }
                    scenario.onActivity {
                        renderer.resumed(true)
                        binding.scanScroll.scrollTo(0, binding.scanContent.height)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertFalse(binding.scanningRing.getGlobalVisibleRect(android.graphics.Rect()))
                        assertEquals(0f, binding.scanningRing.rotation)
                        renderer.render(DeviceScanState(values = List(6) { DeviceInfoValue.Done }))
                        assertEquals(0f, binding.engineRow.itemStatus.rotation)
                        assertEquals(View.GONE, binding.scanningRing.visibility)
                    }
                } finally { scenario.onActivity { renderer.dispose() } }
            }
        } finally { instrumentation.runOnMainSync { guard?.close() } }
    }

    private fun renderAndCheck(tag: String, scale: Float, width: Int, height: Int, state: DeviceScanState, name: String) {
        instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply {
                fontScale = scale; densityDpi = 160; screenWidthDp = width; screenHeightDp = height
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val binding = ScreenDeviceScanBinding.inflate(LayoutInflater.from(themed))
            val renderer = DeviceScanRenderer(binding)
            try {
                // 设计截图含 44dp 系统栏；这里只在测试视口保留同等空间，不伪造时间/信号图标。
                binding.scanScroll.setPadding(0, 44, 0, 0)
                binding.scanContent.setPadding(16, 44, 16, 88)
                renderer.render(state)
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, width, height)
                val rows = listOf(binding.deviceRow, binding.systemRow, binding.resolutionRow, binding.densityRow,
                    binding.storageRow, binding.engineRow)
                for ((previous, next) in rows.zipWithNext()) {
                    assertTrue("$tag/$scale cards must have separation", next.root.top - previous.root.bottom >= 12)
                }
                fun checkText(view: View) {
                    if (view is TextView && view.visibility == View.VISIBLE && view.text.isNotEmpty()) {
                        val layout = requireNotNull(view.layout)
                        assertTrue("$tag/$scale clipped ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                        assertEquals("$tag/$scale missing text ${view.text}", view.text.length, layout.getLineEnd(layout.lineCount - 1))
                        for (line in 0 until layout.lineCount) assertEquals(0, layout.getEllipsisCount(line))
                    }
                    if (view is ViewGroup) for (i in 0 until view.childCount) checkText(view.getChildAt(i))
                }
                checkText(binding.root)
                fun save(suffix: String) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        binding.root.draw(Canvas(bitmap))
                        File(output, "$tag-$scale-$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
                save("top")
                binding.scanScroll.scrollTo(0, binding.scanContent.height)
                save("bottom")
                val button = binding.scanAction
                assertTrue("$tag/$scale button is reachable", button.bottom - binding.scanScroll.scrollY <= height - 44)
                assertTrue(button.height >= 48)
            } finally { renderer.dispose() }
        }
    }
}
