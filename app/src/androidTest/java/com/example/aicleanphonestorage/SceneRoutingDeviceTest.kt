package com.example.aicleanphonestorage

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.feature.push.*
import com.example.aicleanphonestorage.feature.startup.*
import io.docview.push.NotificationDestination
import io.docview.push.analytics.NotificationTriggerSource
import io.docview.push.builder.*
import io.docview.push.scene.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneRoutingDeviceTest {
    @get:org.junit.Rule val noHotStartAds = com.example.aicleanphonestorage.testing.NoHotStartAdsRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun settingsUsesHostRuntimeAndKeepsAllSwitchLabelsMeasurable() {
        androidx.test.core.app.ActivityScenario.launch(ScenePushSettingsActivity::class.java).use { scenario ->
            val ready = java.util.concurrent.atomic.AtomicBoolean()
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            do {
                scenario.onActivity { ready.set(it.findViewById<android.view.View>(io.docview.push.R.id.allScenes).isEnabled) }
                if (!ready.get()) android.os.SystemClock.sleep(50)
            } while (!ready.get() && android.os.SystemClock.elapsedRealtime() < deadline)
            assertTrue("Host scene repository must load", ready.get())
            scenario.onActivity { activity ->
                val options = activity.findViewById<android.widget.LinearLayout>(io.docview.push.R.id.options)
                assertEquals(8, options.childCount)
                for (index in 0 until options.childCount) {
                    val label = options.getChildAt(index) as android.widget.TextView
                    assertTrue(label.text.isNotBlank())
                    assertTrue("Switch label clipped", label.layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                }
                val root = activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    root.draw(android.graphics.Canvas(bitmap))
                    val output = java.io.File(context.getExternalFilesDir(null), "scene-settings-${activity.resources.configuration.fontScale}.png")
                    output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }
    }
    @Test fun everySceneKeepsSourceAndSmallPayloadAcrossSplashHomeAndDestination() {
        for(scene in PushScene.entries) {
            val ref=if(scene in setOf(PushScene.APP_INSTALL,PushScene.APP_UNINSTALL))"test.new.app" else "content://media/external/file/42"
            val input=Intent(context,StartupActivity::class.java).putExtra("from_notification",true)
                .putExtra(LANDING_NOTIFICATION_ACTION,scene.code).putExtra(LANDING_NOTIFICATION_FROM,"scene_push")
                .putExtra(NotificationTriggerSource.EXTRA,scene.source.value).putExtra(LANDING_NOTIFICATION_TITLE,"").putExtra(LANDING_NOTIFICATION_CONTENT,"Charging started! View status.")
            SceneLandingData(scene.code,ref,123456,80,120000).write(input)
            val entry=StartupNavigation.read(input)
            assertEquals(scene.destination,entry.destination)
            assertEquals(scene.source.value,entry.notification?.triggerSource)
            assertEquals("",entry.notification?.content?.title)
            assertEquals("Charging started! View status.",entry.notification?.content?.text)
            assertFalse(entry.launcher)
            val normalized=StartupNavigation.read(StartupNavigation.startupIntent(context,entry))
            assertEquals(entry,normalized)
            val home=StartupNavigation.homeIntent(context,normalized)
            val snapshot=requireNotNull(SceneLandingData.read(home))
            assertEquals(scene.code,snapshot.code)
            val hints=SceneNotificationHints(SavedStateHandle())
            hints.replace(snapshot)
            SceneLandingData.clear(home)
            assertEquals(scene.destination,NotificationNavigation.consume(home))
            assertNull(NotificationNavigation.consume(home))
            val feature=Intent()
            hints.transfer(scene.destination,feature)
            assertEquals(snapshot,SceneLandingData.read(feature))
            val repeated=Intent();hints.transfer(scene.destination,repeated)
            assertNull(SceneLandingData.read(repeated))
        }
    }
    @Test fun invalidDynamicTargetsCannotEscapeMediaOrPackageWhitelist() {
        val malicious=Intent().putExtra(SceneLandingData.CODE,1105).putExtra(SceneLandingData.REFERENCE,"content://other.app.private/secrets/1")
            .putExtra(SceneLandingData.PERCENT,1000).putExtra(SceneLandingData.DURATION,Long.MAX_VALUE)
        val data=requireNotNull(SceneLandingData.read(malicious))
        assertEquals("",data.reference);assertNull(data.batteryPercent);assertNull(data.chargeDurationMs)
        malicious.putExtra(SceneLandingData.CODE,9999)
        assertNull(SceneLandingData.read(malicious));assertEquals(NotificationDestination.HOME,NotificationDestination.fromContentType(9999))
    }
}
