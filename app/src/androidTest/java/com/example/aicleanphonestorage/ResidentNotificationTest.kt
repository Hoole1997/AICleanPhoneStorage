package com.example.aicleanphonestorage

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import com.example.aicleanphonestorage.feature.push.ResidentBadges
import io.docview.push.NotificationDestination
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 真机 RemoteViews apply 与系统通知验证；仅输出测试角标截图，不向业务源写入示例结果。 */
@RunWith(AndroidJUnit4::class)
class ResidentNotificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun residentNotificationUsesSystemChannelAndHomePendingIntent() {
        // 需要先通过 Android 系统对话框允许此测试 App 的通知；不在测试中绕过权限。
        org.junit.Assume.assumeTrue(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        (context.applicationContext as CleanApplication).notificationRuntime.refreshResident()
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var posted = manager.activeNotifications.firstOrNull { it.id == io.docview.push.controller.TriggerCtrl.getResidentNotificationId() }
        while (posted == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50)
            posted = manager.activeNotifications.firstOrNull { it.id == io.docview.push.controller.TriggerCtrl.getResidentNotificationId() }
        }
        val notification = requireNotNull(posted).notification
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(NotificationNavigation.pendingIntent(context, NotificationDestination.HOME), notification.contentIntent)
        assertNotNull(notification.contentView)
        assertNotNull(notification.bigContentView)
    }

    @Test fun independentPendingIntentsAndOneTimeRouting() {
        val intents = NotificationDestination.entries.map { NotificationNavigation.pendingIntent(context, it) }
        assertEquals(intents.size, intents.toSet().size)
        val intent = android.content.Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, "network")
        assertEquals(NotificationDestination.NETWORK, NotificationNavigation.consume(intent))
        assertNull(NotificationNavigation.consume(intent))
    }

    @Test fun renderRemoteViewsAndCheckTextBounds() {
        for (width in listOf(280, 343, 600)) for (font in listOf(1f, 1.3f, 2f)) {
            for (compact in listOf(true, false)) render(width, font, compact)
        }
    }

    private fun render(width: Int, font: Float, compact: Boolean) {
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply {
                densityDpi = 320
                fontScale = font
                setLocale(java.util.Locale.US)
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_AICleanPhoneStorage)
            val host = CleanNotificationHost(context)
            // 使用完整容量与短数量核验角标；空角标无法覆盖实际字体与内边距的边界。
            host.updateBadges(ResidentBadges(clean = "679.9MB", network = "112", photos = "7"))
            val remote = host.residentViews(compact, themed)
            val root = remote.apply(themed, null)
            root.measure(View.MeasureSpec.makeMeasureSpec(width * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            // 此 fixture 是自然用户，第四个入口隐藏是业务要求；GONE 容器的子文字没有 Layout。
            assertEquals(View.GONE, root.findViewById<View>(R.id.shortcut_unused).visibility)
            val labels = listOf(R.id.shortcut_clean_label, R.id.shortcut_network_label, R.id.shortcut_photos_label, R.id.shortcut_unused_label)
            for (id in labels) {
                val label = root.findViewById<TextView>(id)
                // 不用 isShown：RemoteViews 测试根节点尚未附着窗口，但可见内容仍须检查。
                if (generateSequence<View>(label) { it.parent as? View }.any { it.visibility == View.GONE }) continue
                assertTrue("$width/$font: text height", label.height >= label.layout.height + label.paddingTop + label.paddingBottom)
                if (!compact) for (line in 0 until label.lineCount)
                    assertEquals("$width/$font: expanded label must fit", 0, label.layout.getEllipsisCount(line))
            }
            val badge = root.findViewById<TextView>(R.id.shortcut_clean_badge)
            if (badge.visibility == View.VISIBLE) {
                assertTrue("Badge text height", badge.height >= badge.layout.height + badge.paddingTop + badge.paddingBottom)
                if (font <= 1.3f) assertEquals("Capacity badge must fit at $width/$font", 0, badge.layout.getEllipsisCount(0))
            }
            if (compact) assertTrue("Collapsed custom area must fit 48dp: ${root.height / 2}", root.height <= 96)
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it); canvas.drawColor(android.graphics.Color.WHITE); root.draw(canvas)
            }
        }
        val directory = File(context.cacheDir, "resident-rendering").apply { mkdirs() }
        File(directory, "${width}_${font}_${compact}.png").outputStream().use {
            bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap?.recycle()
    }

    @Test fun badgesAreOptionalBoundedAndIndependentlyAddressable() {
        instrumentation.runOnMainSync {
            val host = CleanNotificationHost(context)
            val empty = host.residentViews(false).apply(context, null)
            assertEquals(View.GONE, empty.findViewById<View>(R.id.shortcut_clean_badge).visibility)
            host.updateBadges(ResidentBadges(clean = "1234567890123", photos = "7"))
            val root = host.residentViews(false).apply(context, null)
            assertEquals("12345678", root.findViewById<TextView>(R.id.shortcut_clean_badge).text.toString())
            assertEquals(View.GONE, root.findViewById<View>(R.id.shortcut_network_badge).visibility)
            assertEquals("7", root.findViewById<TextView>(R.id.shortcut_photos_badge).text.toString())
        }
    }
}
