package com.example.aicleanphonestorage

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import com.example.aicleanphonestorage.feature.startup.*
import io.docview.push.NotificationDestination
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.builder.entryPointIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupNavigationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun launcherAndModuleNotificationsResolveToStartupActivity() {
        assertEquals(StartupActivity::class.java.name, context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className)
        assertEquals(StartupActivity::class.java.name, entryPointIntent(context).component?.className)
    }

    @Test fun allNotificationTargetsSurviveStartupAndReachHomeOnce() {
        for (target in NotificationDestination.entries) {
            val fromModule = entryPointIntent(context).putExtra(LANDING_NOTIFICATION_ACTION, target.contentType)
                .putExtra("landing_notification_content", "not forwarded")
            val entry = StartupNavigation.read(fromModule)
            assertEquals(target, entry.destination)
            val home = StartupNavigation.homeIntent(context, entry)
            assertEquals(MainActivity::class.java.name, home.component?.className)
            assertFalse(StartupNavigation.needsStartup(home))
            assertFalse(home.hasExtra("landing_notification_content"))
            assertEquals(target, NotificationNavigation.consume(home))
            assertNull(NotificationNavigation.consume(home))
            val resident = Intent().putExtra(NotificationNavigation.EXTRA_DESTINATION, target.key)
            assertEquals(target, StartupNavigation.read(resident).destination)
        }
    }

    @Test fun legacyMainNotificationsRedirectAndFirebaseConsolePayloadUsesWhitelist() {
        val legacy = Intent(context, MainActivity::class.java).putExtra(NotificationNavigation.EXTRA_DESTINATION, "network")
        assertTrue(StartupNavigation.needsStartup(legacy))
        val console = Intent().putExtra("google.message_id", "test").putExtra("destination", "photos")
        assertEquals(NotificationDestination.PHOTOS, StartupNavigation.read(console).destination)
        console.putExtra("destination", "intent://arbitrary")
        assertEquals(NotificationDestination.HOME, StartupNavigation.read(console).destination)
    }
}
