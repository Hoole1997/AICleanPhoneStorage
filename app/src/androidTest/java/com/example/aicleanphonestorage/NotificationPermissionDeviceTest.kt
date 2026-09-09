package com.example.aicleanphonestorage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.remax.notification.NotificationPermissionAccess
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 运行前由 adb 撤销测试包通知权限并重置请求标记；在真实系统弹框内授权验证 onResume 与库回调。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NotificationPermissionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation

    @Test fun onResumeRequestsMissingPermissionAndGrantPublishesResident() {
        // 撤权会终止应用进程，必须由测试启动前的 adb 完成，不能在 instrumentation 进程内撤权。
        org.junit.Assume.assumeFalse(NotificationPermissionAccess.isGranted(context))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { button("permission_allow_button") != null }
                val screenshot = automation.takeScreenshot()
                try {
                    File(context.cacheDir, "xxpermissions-system-request.png").outputStream().use {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { screenshot.recycle() }
                assertTrue(requireNotNull(button("permission_allow_button"))
                    .performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { NotificationPermissionAccess.isGranted(context) }
                val manager = context.getSystemService(NotificationManager::class.java)
                await { manager.activeNotifications.any { it.id == 4101 } }
                // 再进入首页只检查并刷新，不再显示系统权限请求。
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertNull(button("permission_allow_button"))
            }
    }

    @Test fun notificationChannelDisabledIsReportedBySharedLibraryCheck() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "xxpermissions-test-blocked"
        try {
            manager.createNotificationChannel(NotificationChannel(id, "Permission test", NotificationManager.IMPORTANCE_NONE))
            assertFalse(NotificationPermissionAccess.isGranted(context, id))
        } finally { manager.deleteNotificationChannel(id) }
    }

    private fun button(name: String): AccessibilityNodeInfo? = automation.rootInActiveWindow
        ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$name")?.firstOrNull()

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Expected system permission/notification state within 8 seconds", condition())
    }

}
