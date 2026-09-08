package com.example.aicleanphonestorage

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.core.permissions.*
import com.example.aicleanphonestorage.databinding.DialogAppPermissionBinding
import com.example.aicleanphonestorage.feature.notifications.service.NotificationCleanerService
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 不授予/撤销系统权限；验证品牌弹框、选择结果、公开 Intent 和自身任务栈配置。 */
@RunWith(AndroidJUnit4::class)
class PermissionUiDeviceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun everyPermissionDialogMeasuresNormallyAndWithLargeFonts() {
        for (kind in PermissionKind.entries) for (scale in listOf(1f, 2f)) {
            var image: Bitmap? = null
            instrumentation.runOnMainSync {
                val config =
                    Configuration(context.resources.configuration).apply { fontScale = scale }
                val themed =
                    ContextThemeWrapper(
                        context.createConfigurationContext(config),
                        R.style.Theme_AICleanPhoneStorage,
                    )
                val fragment = PermissionDialogFragment.create("test", kind, false)
                val root = fragment.onCreateView(LayoutInflater.from(themed), null, null)
                val binding = DialogAppPermissionBinding.bind(root)
                val density = themed.resources.displayMetrics.density
                val width = (315 * density).toInt()
                val maxHeight = (560 * density).toInt()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                )
                root.layout(0, 0, width, root.measuredHeight)
                assertTrue(binding.permissionTitle.height >= binding.permissionTitle.layout.height)
                assertTrue(
                    binding.permissionMessage.height >= binding.permissionMessage.layout.height
                )
                assertTrue(binding.permissionContinue.height >= 48 * density)
                assertTrue(root.height <= maxHeight)
                image =
                    Bitmap.createBitmap(width, root.height, Bitmap.Config.ARGB_8888).also {
                        root.draw(Canvas(it))
                    }
            }
            val directory =
                File(context.getExternalFilesDir(null), "permission-ui-tests").apply { mkdirs() }
            image!!.let { bitmap ->
                File(directory, "${kind}_$scale.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    @Test
    fun settingsAreEphemeralAndReturnReusesApplicationTask() {
        val component = ComponentName(context, MainActivity::class.java)
        @Suppress("DEPRECATION")
        val activityInfo = context.packageManager.getActivityInfo(component, 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
        val kinds = buildList {
            add(PermissionKind.USAGE)
            add(PermissionKind.NOTIFICATIONS)
            add(PermissionKind.PHOTOS)
            if (Build.VERSION.SDK_INT >= 30) add(PermissionKind.ALL_FILES)
        }
        for (kind in kinds) for (intent in
            PermissionSettingsNavigator.intents(
                kind,
                context.packageName,
                ComponentName(context, NotificationCleanerService::class.java),
            )) {
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_HISTORY != 0)
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val returned = PermissionSettingsNavigator.returnIntent(component, "request")
        assertEquals(component, returned.component)
        assertTrue(returned.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(returned.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(0, returned.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    @Test
    fun homePermissionExplanationSurvivesRecreationAndCanBeCancelled() {
        assumeTrue(!PermissionChecks.usage(context))
        assumeTrue(
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText(R.string.home_tool_network)).perform(click())
            val deadline = SystemClock.uptimeMillis() + 5000
            var shown = false
            while (!shown && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    shown =
                        it.supportFragmentManager.findFragmentByTag(PermissionDialogFragment.TAG) !=
                            null
                }
                if (!shown) SystemClock.sleep(25) // 仅测试线程等待异步的权限检查。
            }
            assertTrue("Home must use the shared permission dialog", shown)
            scenario.recreate()
            onView(withId(R.id.permission_title))
                .check(matches(withText(R.string.permission_usage_title)))
            onView(withId(R.id.permission_cancel)).perform(click())
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertNull(
                    it.supportFragmentManager.findFragmentByTag(PermissionDialogFragment.TAG)
                )
            }
            assertFalse(PermissionChecks.usage(context))
        }
    }

    @Test
    fun folderAlternativeReturnsChoiceWithoutGrantingOrOpeningSystemPages() {
        assumeTrue(
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var action: String? = null
                activity.supportFragmentManager.setFragmentResultListener(
                    PermissionDialogFragment.RESULT,
                    activity,
                ) { _, result ->
                    action = result.getString("action")
                }
                val dialog =
                    PermissionDialogFragment.create("test", PermissionKind.ALL_FILES, false)
                dialog.showNow(activity.supportFragmentManager, "permission.ui.test")
                dialog.requireView().findViewById<View>(R.id.permission_folder).performClick()
                assertEquals("folder", action)
            }
        }
    }
}
