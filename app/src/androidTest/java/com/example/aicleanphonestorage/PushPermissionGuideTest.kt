package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.databinding.DialogPushPermissionGuideBinding
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.settings.AboutActivity
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 用可控权限回调验证拒绝分支，不修改设备通知权限或请求真实广告。 */
@RunWith(AndroidJUnit4::class)
class PushPermissionGuideTest {
    @get:org.junit.Rule val noHotAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private class Permissions : PushPermissionRequester {
        var granted = false
        var settingsRequired = false
        val requests = mutableListOf<PushPermissionRequest>()
        lateinit var callback: (PushPermissionOutcome) -> Unit

        override fun isGranted() = granted

        override fun needsSettings(origin: PushPermissionRequest) =
            settingsRequired

        override fun request(origin: PushPermissionRequest, onStarted: () -> Unit, result: (PushPermissionOutcome) -> Unit) {
            onStarted()
            requests += origin
            callback = result
        }

        fun complete(granted: Boolean, denied: Boolean) {
            this.granted = granted
            callback(if (granted) PushPermissionOutcome.ALLOWED else if (denied) PushPermissionOutcome.DENIED else PushPermissionOutcome.UNAVAILABLE)
        }
    }

    @Test
    fun systemDenialDoesNotShowCustomGuideInTheSameHost() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val permissions = Permissions()
            lateinit var coordinator: PushPermissionCoordinator
            scenario.onActivity { activity ->
                coordinator = PushPermissionCoordinator(activity,
                    (activity.application as CleanApplication).notificationRuntime,
                    PushPermissionViewModel(SavedStateHandle()), PushPermissionPosition.SPLASH, { false }, { error("No settings") }, permissions,
                    allowGuide = false, events = com.example.aicleanphonestorage.core.analytics.EventSink { _, _ -> })
                coordinator.onResume()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(listOf(PushPermissionRequest.AUTOMATIC), permissions.requests)
                permissions.complete(false, true)
                coordinator.drain()
                assertNull(activity.supportFragmentManager.findFragmentByTag(PushPermissionGuideDialog.TAG))
            }
        }
    }

    @Test
    fun permanentDenialShowsHomeGuideAndAllowOpensSettingsOnce() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val permissions = Permissions().apply { settingsRequired = true }
            var opened = 0
            var pending = false
            lateinit var coordinator: PushPermissionCoordinator
            scenario.onActivity { activity ->
                coordinator = PushPermissionCoordinator(activity,
                    (activity.application as CleanApplication).notificationRuntime,
                    PushPermissionViewModel(SavedStateHandle()), PushPermissionPosition.HOME, { pending }, { opened++; pending = true }, permissions,
                    events = com.example.aicleanphonestorage.core.analytics.EventSink { _, _ -> })
                coordinator.onResume()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(permissions.requests.isEmpty())
                val guide = activity.supportFragmentManager.findFragmentByTag(PushPermissionGuideDialog.TAG) as PushPermissionGuideDialog
                guide.requireView().findViewById<View>(R.id.push_guide_allow).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1, opened)
                assertTrue(permissions.requests.isEmpty())
                coordinator.onResume()
                assertEquals(1, opened)
                permissions.granted = true; pending = false
                coordinator.onSettingsResult(true)
                coordinator.onResume()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(PushPermissionGuideDialog.TAG)) }
        }
    }

    @Test
    fun layoutMatchesFigmaAndLargeFontsRemainScrollable() {
        for (width in listOf(320, 375, 600)) for (scale in listOf(1f, 2f)) {
            var image: Bitmap? = null
            instrumentation.runOnMainSync {
                val configured =
                    context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply {
                            fontScale = scale
                            setLocale(Locale.US)
                        }
                    )
                val themed = ContextThemeWrapper(configured, R.style.Theme_AICleanPhoneStorage)
                val binding = DialogPushPermissionGuideBinding.inflate(LayoutInflater.from(themed))
                val density = themed.resources.displayMetrics.density
                val pixels = (width * density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                        (600 * density).toInt(),
                        View.MeasureSpec.AT_MOST,
                    ),
                )
                binding.root.layout(0, 0, pixels, binding.root.measuredHeight)
                assertTrue(binding.pushGuideClose.height >= 48 * density)
                assertTrue(binding.pushGuideAllow.height >= 48 * density)
                for (text in
                    listOf(
                        binding.pushGuideTitle,
                        binding.pushGuideMessage,
                        binding.pushGuideAllow,
                    )) {
                    assertTrue(
                        text.height - text.compoundPaddingTop - text.compoundPaddingBottom >=
                            text.layout.height
                    )
                }
                // 正文自然测量，OEM 字体度量会改变总高；验证内容顺序和可滚动边界，不能锁死设计稿总高。
                assertTrue(binding.pushGuideTitle.bottom <= binding.pushGuideMessage.top)
                assertTrue(binding.pushGuideMessage.bottom <= binding.pushGuideAllow.top)
                assertTrue(binding.pushGuideAllow.bottom <= binding.root.getChildAt(0).height)
                assertTrue(binding.root.height <= 600 * density)
                image =
                    Bitmap.createBitmap(pixels, binding.root.height, Bitmap.Config.ARGB_8888).also {
                        Canvas(it).apply {
                            drawColor(android.graphics.Color.WHITE)
                            binding.root.draw(this)
                        }
                    }
            }
            save(image!!, "layout_${width}_$scale")
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val directory =
            File(context.getExternalFilesDir(null), "push-guide-tests").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
