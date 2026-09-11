package com.example.aicleanphonestorage

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import io.docview.push.NotificationDestination
import io.docview.push.builder.GeneralModelManager
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.builder.entryPointIntent
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 检查配置 → 普通通知构建 → 首页消费，覆盖所有业务动作，包含以前遗漏的截图/首页图标。 */
@RunWith(AndroidJUnit4::class)
class NotificationBusinessRoutingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun generalAndResidentIntentsShareTheBusinessContract() {
        runBlocking { (context.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        val host = CleanNotificationHost(context)
        val icons = mapOf(
            NotificationDestination.CLEAN to R.drawable.ic_resident_clean,
            NotificationDestination.NETWORK to R.drawable.ic_tool_network,
            NotificationDestination.PHOTOS to R.drawable.ic_tool_compress,
            NotificationDestination.UNUSED_FILES to R.drawable.ic_tool_unused_files,
            NotificationDestination.SCREENSHOTS to R.drawable.ic_tool_screenshots,
            NotificationDestination.HOME to R.mipmap.ic_launcher,
            NotificationDestination.LARGE_FILES to R.drawable.ic_tool_large_files,
            NotificationDestination.NOTIFICATION_CLEANER to R.drawable.ic_tool_notifications,
            NotificationDestination.APP_MANAGER to R.drawable.ic_tool_apps,
        )
        val pendingIntents = NotificationDestination.entries.map { destination ->
            val content = Content("test", "Review", "Choose what to keep", "Open", destination.contentType, destination.contentType)
            val model = GeneralModelManager().build(context, content, CheckCtrl.NotificationType.FCM)
            assertEquals(icons[destination], host.contentIcon(content.iconDestination))
            assertNotNull(model.contentView)
            assertNotNull(model.bigContentView)
            val intent = entryPointIntent(context).putExtra(LANDING_NOTIFICATION_ACTION, content.destination.contentType)
            assertEquals(destination, NotificationNavigation.consume(intent))
            assertNull(NotificationNavigation.consume(intent))
            val resident = Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, destination.key)
            assertEquals(destination, NotificationNavigation.consume(resident))
            requireNotNull(model.contentIntent)
        }
        assertEquals("Different business cards must not overwrite one another's PendingIntent", pendingIntents.size, pendingIntents.toSet().size)
    }

    @Test fun malformedOrUnsupportedLandingDoesNotStartACleaningScan() {
        assertEquals(NotificationDestination.HOME, NotificationNavigation.consume(entryPointIntent(context)))
        assertEquals(NotificationDestination.HOME,
            NotificationNavigation.consume(entryPointIntent(context).putExtra(LANDING_NOTIFICATION_ACTION, 999)))
    }
}
