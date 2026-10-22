package io.docview.push.scene

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.docview.push.NotificationDestination
import io.docview.push.NotificationHost
import io.docview.push.R
import io.docview.push.analytics.NotificationVisibility
import io.docview.push.builder.*
import io.docview.push.controller.TriggerCtrl
import io.docview.push.host.PushEnvironment
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

class SceneRenderTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(FrameLayout(this)) }
}

@RunWith(AndroidJUnit4::class)
class SceneNotificationDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private val shown=mutableListOf<Map<String,Any?>>()
    private lateinit var host:NotificationHost
    @Before fun prepare() {
        check(context.packageName == "io.docview.push.test") // 所有磁盘/通知夹具都属于独立测试 APK。
        context.getSharedPreferences("scene_push_choices",Context.MODE_PRIVATE).edit().clear().commit()
        host=object:NotificationHost {
            override val smallIcon=android.R.drawable.stat_notify_more
            override val appName="Scene test"
            override val residentChannelName="Scene test resident"
            override val pushChannelName="Scene test legacy"
            override val versionName="test"
            override fun contentIntent(destination:NotificationDestination)=PendingIntent.getActivity(context,1,Intent(context,SceneRenderTestActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            override fun residentViews(compact:Boolean)=RemoteViews(context.packageName,R.layout.layout_notification_general)
            override fun contentIcon(destination:NotificationDestination)=R.drawable.scene_network
            override fun onEvent(name:String,properties:Map<String,Any?>) { if(name=="Notific_Show")shown+=properties }
        }
        PushEnvironment.install(context,host)
        NotificationVisibility.install(context.applicationContext as android.app.Application)
        TriggerCtrl.initializeChannels(context)
        ScenePublisher(context).ensureChannel()
    }
    private fun copies()=context.assets.open("scene_push_content.json").bufferedReader().use {SceneCopies.parse(it.readText())}
    private fun event(scene:PushScene)=SceneEvent(scene,System.currentTimeMillis(),0,
        reference=if(scene in setOf(PushScene.APP_INSTALL,PushScene.APP_UNINSTALL))"test.example.app" else "content://media/external/file/42",
        batteryPercent=if(scene==PushScene.CHARGE_END)100 else 20,fullCharge=scene==PushScene.CHARGE_END,
        charging=scene==PushScene.CHARGE_START,plugged=scene in setOf(PushScene.CHARGE_START,PushScene.CHARGE_END),network=SceneNetwork.MOBILE)

    @Test fun richTextUsesSafeNativeSpansAndNeverShowsMarkup() {
        val value=SceneRichText.render("<b>Charging started!</b> View <font color='#336699'>status</font>.<script>hidden()</script>") as Spanned
        assertEquals("Charging started! View status.",value.toString())
        assertTrue(value.getSpans(0,value.length,StyleSpan::class.java).any {it.style and Typeface.BOLD !=0})
        val color=value.getSpans(0,value.length,ForegroundColorSpan::class.java).single()
        assertEquals(Color.rgb(51,102,153),color.foregroundColor)
        assertEquals("status",value.subSequence(value.getSpanStart(color),value.getSpanEnd(color)).toString())
        assertFalse(value.toString().contains("hidden"))
    }

    @Test fun boldAndStrongWithoutHtmlColorNeverCreateColorSpans() {
        for(tag in listOf("b","strong")) {
            val text=SceneRichText.render("<$tag>New file saved!</$tag> Manage files.") as Spanned
            assertEquals("New file saved! Manage files.",text.toString())
            assertTrue(text.getSpans(0,text.length,ForegroundColorSpan::class.java).isEmpty())
            val bold=text.getSpans(0,text.length,StyleSpan::class.java).single()
            assertEquals(Typeface.BOLD,bold.style)
            assertEquals(0,text.getSpanStart(bold))
            assertEquals("New file saved!".length,text.getSpanEnd(bold))
        }
    }

    @Test fun configuredColorsArePreservedIndependentlyOfBoldAndTagNesting() {
        for(html in listOf("<font color='#12AB34'><b>Green</b></font>",
            "<strong><font color=\"#12AB34\">Green</font></strong>")) {
            val text=SceneRichText.render("$html <font color='#336699'>Blue</font> Normal") as Spanned
            assertEquals("Green Blue Normal",text.toString())
            val colors=text.getSpans(0,text.length,ForegroundColorSpan::class.java)
            assertEquals(2,colors.size)
            assertEquals(mapOf("Green" to Color.rgb(18,171,52),"Blue" to Color.rgb(51,102,153)),
                colors.associate { text.subSequence(text.getSpanStart(it),text.getSpanEnd(it)).toString() to it.foregroundColor })
            val bold=text.getSpans(0,text.length,StyleSpan::class.java).single()
            assertEquals("Green",text.subSequence(text.getSpanStart(bold),text.getSpanEnd(bold)).toString())
        }
    }

    @Test fun everyLocalDefaultColorsOnlyItsHtmlEmphasis() {
        for(copy in copies().values.flatten()) {
            val text=SceneRichText.render(copy.text) as Spanned
            val bold=text.getSpans(0,text.length,StyleSpan::class.java).single()
            val color=text.getSpans(0,text.length,ForegroundColorSpan::class.java).single()
            assertEquals(copy.id,Color.rgb(251,73,73),color.foregroundColor)
            assertEquals(text.getSpanStart(bold),text.getSpanStart(color))
            assertEquals(text.getSpanEnd(bold),text.getSpanEnd(color))
            assertEquals(copy.id,0,text.getSpanStart(color))
            assertTrue(copy.id,text.getSpanEnd(color)<text.length)
        }
    }

    @Test fun d1RegionalTranslationsRenderTheirHtmlAndButtons() {
        val languages=listOf("pt-BR","es-MX","es-ES","id-ID","hi-IN","ja-JP","ko-KR")
        val content=copies()
        for(copy in content.values.flatten()) for(language in languages) {
            val translated=copy.text(language)
            val rendered=SceneRichText.render(translated.text) as Spanned
            assertFalse("${copy.id}/$language",rendered.toString().contains('<'))
            val color=rendered.getSpans(0,rendered.length,ForegroundColorSpan::class.java).single()
            val bold=rendered.getSpans(0,rendered.length,StyleSpan::class.java).single()
            assertEquals(Color.rgb(251,73,73),color.foregroundColor)
            assertEquals(rendered.getSpanStart(bold),rendered.getSpanStart(color))
            assertEquals(rendered.getSpanEnd(bold),rendered.getSpanEnd(color))
            assertTrue(translated.button.isNotBlank())
        }
        val output=File(context.getExternalFilesDir(null),"scene-language-verification").apply { mkdirs() }
        for(language in languages) for(scale in listOf(1f,2f)) instrumentation.runOnMainSync {
            val configured=context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(language));fontScale=scale
            })
            val e=event(PushScene.CHARGE_START)
            val selected=requireNotNull(SceneCopies.select(content.getValue(e.scene),"",e,language))
            val model=ScenePublisher(configured).model(e,selected,false,configured)
            for((kind,remote) in listOf("compact" to model.contentView!!,"expanded" to model.bigContentView!!)) {
                val parent=FrameLayout(configured)
                val view=remote.apply(configured,parent)
                parent.addView(view)
                remote.reapply(configured,view)
                val width=(320*configured.resources.displayMetrics.density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                view.layout(0,0,width,view.measuredHeight)
                val body=view.findViewById<TextView>(R.id.sceneText)
                assertEquals(SceneRichText.plain(selected.text.text),body.text.toString())
                assertTrue("$language/$scale/$kind glyph height",body.layout.height<=body.height-body.compoundPaddingTop-body.compoundPaddingBottom)
                assertEquals(selected.text.button,view.findViewById<TextView>(R.id.tvAction).text.toString())
                val bitmap=Bitmap.createBitmap(width,view.measuredHeight,Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas)
                    File(output,"${language}_${scale}_$kind.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun eightNotificationIdsCoexistWithLegacyAndCancelIndependently()=runBlocking {
        val publisher=ScenePublisher(context)
        val legacyId=10000
        val legacy=NotificationCompat.Builder(context,TriggerCtrl.CHANNEL_ID_GENERAL).setSmallIcon(host.smallIcon)
            .setContentTitle("Legacy fixture").setSilent(true).build()
        manager.notify(legacyId,legacy)
        val stored=context.getSharedPreferences("browser_push_preferences",Context.MODE_PRIVATE)
        stored.edit().putLong("legacy_test_counter",73).commit()
        val before=stored.all.toMap()
        try {
            assertTrue(NotificationVisibility.backgroundForDisplay())
            val content=copies()
            for(scene in PushScene.entries) {
                val e=event(scene)
                val selected=requireNotNull(SceneCopies.select(content.getValue(scene),"",e,"en"))
                assertTrue(scene.name,publisher.publish(e,selected){true})
            }
            delay(350)
            val active=manager.activeNotifications.associateBy {it.id}
            assertTrue(PushScene.entries.all {it.notificationId in active})
            assertTrue(legacyId in active)
            for(scene in PushScene.entries) {
                val n=active.getValue(scene.notificationId).notification
                assertEquals(ScenePublisher.CHANNEL,n.channelId)
                assertNotNull(n.headsUpContentView)
                assertNull(n.deleteIntent)
                assertNull(n.fullScreenIntent)
                assertNotNull(n.contentIntent)
                assertNull(n.extras.getCharSequence(Notification.EXTRA_TITLE))
                assertEquals(SceneRichText.plain(content.getValue(scene).first { it.applies(event(scene)) }.text),
                    n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            }
            assertEquals(8,shown.size)
            assertTrue(shown.all {it["Notific_Type"]==1 && it["from_background"]=="true"})
            assertEquals(PushScene.entries.map {it.source.value}.toSet(),shown.map {it["trigger_source"]}.toSet())
            assertTrue(shown.all {it["title"]=="" && it["text"].toString().isNotBlank() && !it["text"].toString().contains("<")})
            // 保存 SystemUI 实际面板，补充离屏 RemoteViews 测量；只操作独立测试 APK 的模拟器通知。
            val automation=instrumentation.uiAutomation
            try {
                automation.executeShellCommand("cmd statusbar expand-notifications").close()
                delay(700)
                repeat(2) {
                    automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/expand_button")
                        ?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    delay(350)
                }
                val screenshot=requireNotNull(automation.takeScreenshot())
                try {
                    val output=File(context.getExternalFilesDir(null),"scene-verification").apply {mkdirs()}
                    File(output,"system-notifications.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
                } finally {screenshot.recycle()}
                if(InstrumentationRegistry.getArguments().getString("record_scene_motion")=="true") delay(12_000)
            } finally {automation.executeShellCommand("cmd statusbar collapse").close()}
            manager.cancel(PushScene.SCREENSHOT.notificationId);delay(150)
            val remaining=manager.activeNotifications.map {it.id}.toSet()
            assertFalse(PushScene.SCREENSHOT.notificationId in remaining)
            assertTrue(PushScene.entries.filter {it!=PushScene.SCREENSHOT}.all {it.notificationId in remaining})
            assertTrue(legacyId in remaining)
            assertEquals(before,stored.all)
        } finally {PushScene.entries.forEach {manager.cancel(it.notificationId)};manager.cancel(legacyId);stored.edit().remove("legacy_test_counter").commit()}
    }

    @Test fun originalIconsAndHtmlRenderAtBothFontScalesAndReapplySafely() {
        val output=File(context.getExternalFilesDir(null),"scene-verification").apply{mkdirs()}
        val content=copies()
        for(scene in PushScene.entries) for(scale in listOf(1f,2f)) for(animated in listOf(false,true)) {
            instrumentation.runOnMainSync {
                val configured=context.createConfigurationContext(Configuration(context.resources.configuration).apply {fontScale=scale})
                val model=ScenePublisher(configured).model(event(scene),requireNotNull(SceneCopies.select(content.getValue(scene),"",event(scene),"en")),animated,configured)
                for((kind,views) in listOf("heads_up" to model.contentView!!,"expanded" to model.bigContentView!!)) {
                    val parent=FrameLayout(configured)
                    val view=views.apply(configured,parent)
                    parent.addView(view)
                    views.reapply(configured,view) // 同 ID 更新必须可重用，不能依赖已移除的 ViewStub。
                    val density=configured.resources.displayMetrics.density
                    val width=(320*density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                    view.layout(0,0,width,view.measuredHeight)
                    val title=view.findViewById<TextView>(R.id.sceneText)
                    assertFalse(title.text.toString().contains("<"))
                    assertTrue("$scene/$scale/$kind title clipped",title.layout.height<=title.height-title.compoundPaddingTop-title.compoundPaddingBottom)
                    assertTrue(title.text is Spanned)
                    assertTrue((title.text as Spanned).getSpans(0,title.text.length,ForegroundColorSpan::class.java).isNotEmpty())
                    assertEquals(if(animated) View.VISIBLE else View.GONE, view.findViewById<View>(R.id.sceneButtonMotion).visibility)
                    if(animated) {
                        val slot=view.findViewById<FrameLayout>(R.id.sceneIconSlot)
                        assertEquals(View.VISIBLE,slot.visibility)
                        val drawable=(slot.getChildAt(0) as android.widget.ProgressBar).indeterminateDrawable
                        assertEquals(slot.width, slot.getChildAt(0).width)
                        assertEquals(slot.height, slot.getChildAt(0).height)
                        val originalBounds=android.graphics.Rect(drawable.bounds)
                        val icon=Bitmap.createBitmap(56,56,Bitmap.Config.ARGB_8888)
                        try {
                            drawable.setBounds(0,0,56,56);drawable.draw(Canvas(icon))
                            val colors=HashSet<Int>()
                            for(y in 0 until 56)for(x in 0 until 56){val color=icon.getPixel(x,y);if(Color.alpha(color)>200)colors+=color}
                            assertTrue("Original $scene artwork must not be flattened by ProgressBar tint",colors.size>8)
                        } finally {drawable.bounds=originalBounds;icon.recycle()}
                        val button=(view.findViewById<View>(R.id.sceneButtonMotion) as android.widget.ProgressBar).indeterminateDrawable
                        assertEquals(view.findViewById<View>(R.id.tvAction).width,view.findViewById<View>(R.id.sceneButtonMotion).width)
                        assertEquals(view.findViewById<View>(R.id.tvAction).height,view.findViewById<View>(R.id.sceneButtonMotion).height)
                        assertEquals(view.findViewById<View>(R.id.tvAction).width,button.bounds.width())
                        assertEquals(view.findViewById<View>(R.id.tvAction).height,button.bounds.height())
                        assertTrue("Continuous XML animation must use platform property interpolation", button is android.graphics.drawable.AnimatedVectorDrawable)
                        if(kind=="expanded") assertTrue((view.findViewById<View>(R.id.sceneArrowStart) as android.widget.ProgressBar).indeterminateDrawable is android.graphics.drawable.AnimationDrawable)
                    }
                    if(!animated || scene==PushScene.APP_UNINSTALL) {
                        val bitmap=Bitmap.createBitmap(width,view.measuredHeight,Bitmap.Config.ARGB_8888)
                        try {view.draw(Canvas(bitmap));File(output,"${scene.code}_${scale}_$kind${if(animated) "_animated" else ""}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
                        finally{bitmap.recycle()}
                    }
                }
            }
        }
    }

    @Test fun sentenceUsesOneTextViewAndOnlyMarkedWordsAreRedAndBold() {
        val selected=requireNotNull(SceneCopies.select(copies().getValue(PushScene.CHARGE_START),"",event(PushScene.CHARGE_START),"en"))
        val text=SceneRichText.render(selected.text.text) as Spanned
        assertEquals("Charging started! View status.",text.toString())
        assertFalse(text.contains('\n'))
        val emphasized="Charging started!".length
        assertTrue(text.getSpans(0,text.length,StyleSpan::class.java).all {text.getSpanStart(it)==0 && text.getSpanEnd(it)==emphasized})
        assertTrue(text.getSpans(0,text.length,ForegroundColorSpan::class.java).all {text.getSpanStart(it)==0 && text.getSpanEnd(it)==emphasized})
        instrumentation.runOnMainSync {
            val model=ScenePublisher(context).model(event(PushScene.CHARGE_START),selected,false)
            for(views in listOf(model.contentView!!,model.bigContentView!!)) {
                val view=views.apply(context,FrameLayout(context))
                assertEquals(text.toString(),view.findViewById<TextView>(R.id.sceneText).text.toString())
                assertNull(view.findViewById<View>(R.id.tvTitle))
                assertNull(view.findViewById<View>(R.id.tvDesc))
            }
        }
    }

    @Test fun attachedXmlButtonActuallyAnimatesAndStopsWhenHidden():Unit=runBlocking {
        val samples=mutableSetOf<Int>()
        lateinit var content:View
        lateinit var motion:android.widget.ProgressBar
        ActivityScenario.launch(SceneRenderTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val selected=requireNotNull(SceneCopies.select(copies().getValue(PushScene.CHARGE_START),"",event(PushScene.CHARGE_START),"en"))
                val parent=activity.findViewById<FrameLayout>(android.R.id.content)
                parent.setBackgroundColor(Color.WHITE)
                content=ScenePublisher(context).model(event(PushScene.CHARGE_START),selected,true).bigContentView!!.apply(activity,parent)
                parent.addView(content,FrameLayout.LayoutParams((320*activity.resources.displayMetrics.density).toInt(),FrameLayout.LayoutParams.WRAP_CONTENT,android.view.Gravity.CENTER))
                motion=content.findViewById(R.id.sceneButtonMotion)
            }
            delay(250)
            repeat(12) {
                val bounds=android.graphics.Rect()
                scenario.onActivity { motion.getGlobalVisibleRect(bounds) }
                val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    val pixels=IntArray(bounds.width()*bounds.height())
                    bitmap.getPixels(pixels,0,bounds.width(),bounds.left,bounds.top,bounds.width(),bounds.height())
                    samples+=pixels.contentHashCode()
                } finally {bitmap.recycle()}
                delay(90)
            }
            assertTrue("Sweep/pulse must change on-screen pixels, not just drawable metadata",samples.size>=6)
            scenario.onActivity { content.visibility=View.INVISIBLE }
            delay(100)
            scenario.onActivity { assertFalse((motion.indeterminateDrawable as android.graphics.drawable.Animatable).isRunning) }
        }
        Unit
    }

    @Test fun legacyMotionSupportsBothLayoutsAndStaticReapply(): Unit = runBlocking {
        val data = io.docview.push.config.Content("motion", "Storage summary", "Review your files.", "Clean", 1, 1)
        val model = GeneralModelManager().build(context, data, io.docview.push.check.CheckCtrl.NotificationType.ALARM_MANAGER, animate = true)
        val output = File(context.getExternalFilesDir(null), "motion-verification").apply { mkdirs() }
        for ((kind, remote) in listOf("compact" to model.contentView!!, "expanded" to model.bigContentView!!)) {
            lateinit var content: View
            lateinit var motion: android.widget.ProgressBar
            ActivityScenario.launch(SceneRenderTestActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val parent = activity.findViewById<FrameLayout>(android.R.id.content)
                    parent.setBackgroundColor(Color.WHITE)
                    content = remote.apply(activity, parent)
                    parent.addView(content, FrameLayout.LayoutParams((320 * activity.resources.displayMetrics.density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                    motion = content.findViewById(R.id.notificationButtonMotion)
                }
                delay(250)
                val samples = mutableSetOf<Int>()
                repeat(12) { frame ->
                    val bounds = android.graphics.Rect()
                    scenario.onActivity {
                        motion.getGlobalVisibleRect(bounds)
                        val action = content.findViewById<TextView>(R.id.tvAction)
                        assertTrue(action.layout.height <= action.height - action.compoundPaddingTop - action.compoundPaddingBottom)
                        assertTrue(action.hasOnClickListeners())
                        assertEquals(motion.width, motion.indeterminateDrawable.bounds.width())
                        assertEquals(motion.height, motion.indeterminateDrawable.bounds.height())
                        assertTrue(motion.indeterminateDrawable is android.graphics.drawable.AnimatedVectorDrawable)
                    }
                    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    try {
                        val pixels = IntArray(bounds.width() * bounds.height())
                        bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                        samples += pixels.contentHashCode()
                        if (frame == 6) File(output, "legacy-$kind.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                    delay(100)
                }
                assertTrue("Legacy $kind must visibly animate", samples.size >= 6)
                scenario.onActivity {
                    NotificationMotion.applyLegacy(remote, false)
                    remote.reapply(it, content)
                    assertEquals(View.GONE, motion.visibility)
                    assertEquals(View.VISIBLE, content.findViewById<View>(R.id.notificationButtonStatic).visibility)
                    assertEquals("Clean", content.findViewById<TextView>(R.id.tvAction).text.toString())
                }
                delay(100)
                scenario.onActivity { assertFalse((motion.indeterminateDrawable as android.graphics.drawable.Animatable).isRunning) }
            }
        }
    }

    @Test fun rippleAloneChangesVisiblePixelsWithoutSweepOrBreathing(): Unit = runBlocking {
        lateinit var motion: android.widget.ProgressBar
        val samples = mutableSetOf<Int>()
        ActivityScenario.launch(SceneRenderTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent = activity.findViewById<FrameLayout>(android.R.id.content)
                parent.setBackgroundColor(Color.WHITE)
                motion = android.widget.ProgressBar(activity).apply {
                    isIndeterminate = true
                    indeterminateTintList = null
                    indeterminateDrawable = activity.getDrawable(io.docview.push.test.R.drawable.notification_wave_fixture)
                }
                parent.addView(motion, FrameLayout.LayoutParams((295 * activity.resources.displayMetrics.density).toInt(), (46 * activity.resources.displayMetrics.density).toInt(), android.view.Gravity.CENTER))
            }
            delay(200)
            repeat(14) {
                val bounds = android.graphics.Rect()
                scenario.onActivity { motion.getGlobalVisibleRect(bounds) }
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    val pixels = IntArray(bounds.width() * bounds.height())
                    bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                    samples += pixels.contentHashCode()
                } finally { bitmap.recycle() }
                delay(100)
            }
            assertTrue("Water ripples must actually expand and fade", samples.size >= 8)
            scenario.onActivity { motion.visibility = View.GONE }
            delay(100)
            scenario.onActivity { assertFalse((motion.indeterminateDrawable as android.graphics.drawable.Animatable).isRunning) }
        }
    }

    @Test fun serviceOwnedListenersAreReleasedOnDisableAndClose()=runBlocking {
        var enabled=true
        val runtimeHost=object:NotificationHost by host {
            override suspend fun scenePushParameter(key:String):String?=if(key==ScenePushKeys.CONFIG)"""{"enabled":$enabled}""" else null
        }
        val tracking=TrackingContext(context)
        val repo=SceneRepository(context,runtimeHost)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var registration:AutoCloseable?=null
        try {
            withContext(Dispatchers.Main.immediate){registration=SceneEventRegistration.create(tracking,scope,repo)}
            withTimeout(5000){while(tracking.active<2)delay(20)}
            enabled=false;repo.refresh()
            withTimeout(5000){while(tracking.active!=0)delay(20)}
            enabled=true;repo.refresh()
            withTimeout(5000){while(tracking.active<2)delay(20)}
            withContext(Dispatchers.Main.immediate){registration!!.close();registration=null}
            assertEquals(0,tracking.active)
        } finally {withContext(Dispatchers.Main.immediate){registration?.close()};scope.cancel()}
    }

    @Test fun historyAndUserSwitchesPersistSeparatelyAndDoNotShareSceneCursors()=runBlocking {
        val store=SceneStorage(context)
        store.succeeded(PushScene.SCREENSHOT,SceneMoment(1000,100,7),"screenshot_two")
        store.succeeded(PushScene.DOWNLOAD,SceneMoment(2000,200,7),"download_one")
        store.choose(PushScene.NETWORK,false);store.choose(null,false)
        val restored=SceneStorage(context)
        assertEquals("screenshot_two",restored.history(PushScene.SCREENSHOT).copyId)
        assertEquals("download_one",restored.history(PushScene.DOWNLOAD).copyId)
        assertEquals("",restored.history(PushScene.APP_UNINSTALL).copyId)
        assertFalse(restored.choices().enabled)
        assertEquals(setOf(PushScene.NETWORK),restored.choices().disabled)
        restored.choose(null,true)
        assertTrue(restored.choices().enabled)
        assertEquals(setOf(PushScene.NETWORK),restored.choices().disabled)
    }

    @Test fun sceneRepeatsUseExistingConfigKeepIdsIndependentAndNeverRecountShows()=runBlocking {
        val field=io.docview.push.config.ConfigCtrl::class.java.getDeclaredField("config").apply {isAccessible=true}
        val previous=field.get(io.docview.push.config.ConfigCtrl)
        val tier=io.docview.push.config.Config(3,10,10,1,2,0,"00:00","00:00",1,15)
        field.set(io.docview.push.config.ConfigCtrl,io.docview.push.config.NotificationConfig(tier,tier))
        PushEnvironment.install(context,object:NotificationHost by host {override val repeatNotificationsEnabled=true})
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var repeat:SceneRepeatCoordinator
        withContext(Dispatchers.Main.immediate) {repeat=SceneRepeatCoordinator(context,scope,{true})}
        val publisher=ScenePublisher(context,repeat)
        val first=PushScene.CHARGE_START
        val second=PushScene.NETWORK
        val legacy=10000
        manager.notify(legacy,NotificationCompat.Builder(context,TriggerCtrl.CHANNEL_ID_GENERAL)
            .setSmallIcon(host.smallIcon).setContentTitle("Legacy repeat isolation").setSilent(true).build())
        val legacyPrefs=context.getSharedPreferences("reset_at_midnight_prefs",Context.MODE_PRIVATE)
        val before=legacyPrefs.all.toMap()
        suspend fun iteration(scene:PushScene,value:Int):android.app.Notification=withTimeout(7000) {
            while(true) {
                val active=manager.activeNotifications.firstOrNull {it.id==scene.notificationId}?.notification
                if(active?.extras?.getInt(SceneRepeatCoordinator.EXTRA_ITERATION,-1)==value) return@withTimeout active
                delay(40)
            }
            @Suppress("UNREACHABLE_CODE") error("Unreachable")
        }
        try {
            val content=copies()
            for(scene in listOf(first,second)) assertTrue(publisher.publish(event(scene),
                requireNotNull(SceneCopies.select(content.getValue(scene),"",event(scene),"en"))){true})
            val original=iteration(first,0)
            val firstRepeat=iteration(first,1)
            assertEquals(original.contentIntent,firstRepeat.contentIntent)
            assertEquals(original.extras.getString(SceneRepeatCoordinator.EXTRA_TOKEN),firstRepeat.extras.getString(SceneRepeatCoordinator.EXTRA_TOKEN))
            assertEquals(ScenePublisher.CHANNEL,firstRepeat.channelId)
            iteration(second,1)
            // 单独发送划除回调，暂留卡片以证明是 Receiver 停掉 A，不能只靠“卡片消失”兜底。
            requireNotNull(firstRepeat.deleteIntent).send()
            val last=iteration(second,2)
            assertEquals(1,manager.activeNotifications.first {it.id==first.notificationId}.notification.extras.getInt(SceneRepeatCoordinator.EXTRA_ITERATION))
            manager.cancel(first.notificationId)
            assertEquals(ScenePublisher.SILENT_CHANNEL,last.channelId)
            assertNull(manager.getNotificationChannel(ScenePublisher.SILENT_CHANNEL).sound)
            assertEquals(2,shown.size) // 初次展示两次；循环不重复上报、不影响旧每日计数。
            assertEquals(before,legacyPrefs.all)
            val lastTime=manager.activeNotifications.first {it.id==second.notificationId}.postTime
            delay(4500)
            val active=manager.activeNotifications.associateBy {it.id}
            assertFalse(first.notificationId in active)
            assertEquals(lastTime,active.getValue(second.notificationId).postTime)
            assertTrue(legacy in active)
        } finally {
            withContext(Dispatchers.Main.immediate){repeat.close()};scope.cancel()
            listOf(first.notificationId,second.notificationId,legacy).forEach(manager::cancel)
            field.set(io.docview.push.config.ConfigCtrl,previous)
            PushEnvironment.install(context,host)
        }
    }

    @Test fun queuedScenesWaitForCycleThenSkipInvalidAndAdvanceOnDismissal():Unit=runBlocking {
        val field=io.docview.push.config.ConfigCtrl::class.java.getDeclaredField("config").apply {isAccessible=true}
        val previous=field.get(io.docview.push.config.ConfigCtrl)
        val tier=io.docview.push.config.Config(3,10,10,1,1,0,"00:00","00:00",1,15)
        field.set(io.docview.push.config.ConfigCtrl,io.docview.push.config.NotificationConfig(tier,tier))
        PushEnvironment.install(context,object:NotificationHost by host {override val repeatNotificationsEnabled=true})
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val queue=ScenePresentationQueue(io.docview.push.utils.Logger::d)
        val history=mutableMapOf<PushScene,SceneHistory>()
        val invalid=mutableSetOf<PushScene>()
        val config=SceneConfiguration(ScenePolicy(),copies(),SceneChoices())
        lateinit var repeat:SceneRepeatCoordinator
        lateinit var dispatcher:SceneDispatcher
        val order=listOf(PushScene.CHARGE_START,PushScene.NETWORK,PushScene.DOWNLOAD,PushScene.APP_INSTALL)
        val legacy=10000
        manager.notify(legacy,NotificationCompat.Builder(context,TriggerCtrl.CHANNEL_ID_GENERAL)
            .setSmallIcon(host.smallIcon).setContentTitle("Legacy outside queue").setSilent(true).build())
        try {
            withContext(Dispatchers.Main.immediate) {
                repeat=SceneRepeatCoordinator(context,scope,{true})
                val publisher=ScenePublisher(context,repeat)
                dispatcher=SceneDispatcher(scope,{config},object:SceneHistoryStore {
                    override suspend fun history(scene:PushScene)=history[scene]?:SceneHistory()
                    override suspend fun succeeded(scene:PushScene,moment:SceneMoment,copyId:String) {
                        history[scene]=SceneHistory(moment.wall,moment.elapsed,moment.boot,copyId)
                    }
                },{SceneMoment(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),1)},{"en"},{true},
                    validate={it.scene !in invalid},publish=publisher::publish,presentation=queue,log=io.docview.push.utils.Logger::d)
                order.forEach {dispatcher.offer(event(it))}
            }
            suspend fun shown(scene:PushScene,iteration:Int)=withTimeout(12_000) {
                while(true) {
                    val found=manager.activeNotifications.firstOrNull {it.id==scene.notificationId}
                    if(found?.notification?.extras?.getInt(SceneRepeatCoordinator.EXTRA_ITERATION,-1)==iteration) return@withTimeout found
                    delay(40)
                }
                @Suppress("UNREACHABLE_CODE") error("Unreachable")
            }
            val first=shown(order[0],0)
            withContext(Dispatchers.Main.immediate) {
                // 触发快照在首次展示后改变：只影响未展示候选，不能阻断 A 的第一次循环。
                invalid+=order[0]
                dispatcher.cancel(order[0])
            }
            shown(order[0],1)
            assertFalse(manager.activeNotifications.any {it.id in order.drop(1).map(PushScene::notificationId)})
            withContext(Dispatchers.Main.immediate) {
                assertEquals(setOf(order[0]),history.keys)
                invalid+=PushScene.DOWNLOAD
            }
            val second=shown(order[1],0)
            assertTrue("Next first publication must wait for the final 4s display window",second.postTime-first.postTime>=7_500)
            second.notification.deleteIntent.send() // 主动结束本轮，应跳过已失效的下载并立即推进到安装提醒。
            shown(order[3],0)
            assertFalse(manager.activeNotifications.any {it.id==PushScene.DOWNLOAD.notificationId})
            withContext(Dispatchers.Main.immediate) {
                assertEquals(listOf("charge_start","network_change","app_install"),this@SceneNotificationDeviceTest.shown.map {it["trigger_source"]})
                assertEquals(setOf(order[0],order[1],order[3]),history.keys)
            }
            assertTrue(manager.activeNotifications.any {it.id==legacy})
        } finally {
            withContext(Dispatchers.Main.immediate) {queue.close();dispatcher.close();repeat.close()};scope.cancel()
            (order.map(PushScene::notificationId)+legacy).forEach(manager::cancel)
            field.set(io.docview.push.config.ConfigCtrl,previous);PushEnvironment.install(context,host)
        }
    }

    @Test fun allEightScenesFinishCyclesInFifoAndNeverEnableOnlyAlertOnce():Unit=runBlocking {
        val field=io.docview.push.config.ConfigCtrl::class.java.getDeclaredField("config").apply {isAccessible=true}
        val previous=field.get(io.docview.push.config.ConfigCtrl)
        val tier=io.docview.push.config.Config(3,10,10,1,2,0,"00:00","00:00",1,15)
        field.set(io.docview.push.config.ConfigCtrl,io.docview.push.config.NotificationConfig(tier,tier))
        PushEnvironment.install(context,object:NotificationHost by host {override val repeatNotificationsEnabled=true})
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val queue=ScenePresentationQueue(io.docview.push.utils.Logger::d)
        val history=mutableMapOf<PushScene,SceneHistory>()
        val invalid=mutableSetOf<PushScene>()
        val config=SceneConfiguration(ScenePolicy(),copies(),SceneChoices())
        var repeat:SceneRepeatCoordinator?=null
        var dispatcher:SceneDispatcher?=null
        val legacyId=10000
        val legacyPrefs=context.getSharedPreferences("reset_at_midnight_prefs",Context.MODE_PRIVATE)
        val previousCounts=legacyPrefs.all.toMap()
        val evidence=org.json.JSONArray()
        try {
            manager.notify(legacyId,NotificationCompat.Builder(context,TriggerCtrl.CHANNEL_ID_GENERAL)
                .setSmallIcon(host.smallIcon).setContentTitle("Legacy outside new cycles").setSilent(true).build())
            withContext(Dispatchers.Main.immediate) {
                repeat=SceneRepeatCoordinator(context,scope,{true})
                val publisher=ScenePublisher(context,repeat)
                dispatcher=SceneDispatcher(scope,{config},object:SceneHistoryStore {
                    override suspend fun history(scene:PushScene)=history[scene]?:SceneHistory()
                    override suspend fun succeeded(scene:PushScene,moment:SceneMoment,copyId:String) {
                        history[scene]=SceneHistory(moment.wall,moment.elapsed,moment.boot,copyId)
                    }
                },{SceneMoment(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),1)},{"en"},{true},
                    validate={it.scene !in invalid},publish=publisher::publish,presentation=queue,log=io.docview.push.utils.Logger::d)
                PushScene.entries.forEach {dispatcher!!.offer(event(it))}
            }
            suspend fun awaitIteration(scene:PushScene,iteration:Int)=withTimeout(7_000) {
                while(true) {
                    val found=manager.activeNotifications.firstOrNull {it.id==scene.notificationId}
                    if(found?.notification?.extras?.getInt(SceneRepeatCoordinator.EXTRA_ITERATION,-1)==iteration) return@withTimeout found
                    delay(40)
                }
                @Suppress("UNREACHABLE_CODE") error("Unreachable")
            }
            var previousLast:Long?=null
            for((index,scene) in PushScene.entries.withIndex()) {
                val initial=awaitIteration(scene,0)
                previousLast?.let { assertTrue("${scene.code} cannot interrupt the preceding final window",initial.postTime-it>=3_500) }
                withContext(Dispatchers.Main.immediate) {
                    invalid+=scene;dispatcher!!.cancel(scene)
                    assertEquals(index+1,history.size)
                }
                for(iteration in 0..2) {
                    val posted=if(iteration==0) initial else awaitIteration(scene,iteration)
                    val notification=posted.notification
                    assertFalse("${scene.code}/$iteration must not enable OnlyAlertOnce",
                        notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
                    assertNotNull(notification.headsUpContentView)
                    assertEquals(initial.notification.contentIntent,notification.contentIntent)
                    assertEquals(if(iteration<2) ScenePublisher.CHANNEL else ScenePublisher.SILENT_CHANNEL,notification.channelId)
                    assertFalse("Queued scenes must not publish early",manager.activeNotifications.any { active ->
                        PushScene.entries.drop(index+1).any {it.notificationId==active.id}
                    })
                    evidence.put(org.json.JSONObject().put("scene",scene.code).put("iteration",iteration)
                        .put("postTime",posted.postTime).put("onlyAlertOnce",false).put("channel",notification.channelId))
                    if(iteration==2) previousLast=posted.postTime
                }
            }
            // 最后一条完成后，展示位也必须在最后 4 秒窗口之后归还，不能卡死空队列。
            withTimeout(7_000) { withContext(Dispatchers.Main.immediate) {queue.acquire(PushScene.CHARGE_START)!!.release()} }
            assertTrue(System.currentTimeMillis()-previousLast!!>=3_500)
            assertEquals(8,shown.size);assertEquals(previousCounts,legacyPrefs.all)
            assertTrue(manager.activeNotifications.any {it.id==legacyId})
            val output=File(context.getExternalFilesDir(null),"scene-verification").apply {mkdirs()}
            File(output,"all-scene-cycles.json").writeText(evidence.toString(2))
        } finally {
            withContext(Dispatchers.Main.immediate) {queue.close();dispatcher?.close();repeat?.close()};scope.cancel()
            (PushScene.entries.map(PushScene::notificationId)+legacyId).forEach(manager::cancel)
            field.set(io.docview.push.config.ConfigCtrl,previous);PushEnvironment.install(context,host)
        }
    }

    private class TrackingContext(base:Context):ContextWrapper(base) {
        private val receivers=java.util.Collections.synchronizedSet(mutableSetOf<BroadcastReceiver>())
        val active get()=receivers.size
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter?,flags:Int):Intent? {
            val result=super.registerReceiver(receiver,filter,flags);if(receiver!=null)receivers+=receiver;return result
        }
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter?,permission:String?,scheduler:android.os.Handler?,flags:Int):Intent? {
            val result=super.registerReceiver(receiver,filter,permission,scheduler,flags);if(receiver!=null)receivers+=receiver;return result
        }
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter?,permission:String?,scheduler:android.os.Handler?):Intent? {
            val result=super.registerReceiver(receiver,filter,permission,scheduler);if(receiver!=null)receivers+=receiver;return result
        }
        override fun unregisterReceiver(receiver:BroadcastReceiver) {super.unregisterReceiver(receiver);receivers-=receiver}
    }
}
