package com.example.aicleanphonestorage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.docview.push.builder.GeneralModelManager
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只构建本地样例 RemoteViews，不发布通知、不推进真实轮播、不改变用户语言设置。 */
@RunWith(AndroidJUnit4::class)
class DayPoolNotificationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun localizedTextUsesExistingViewsAndAnalyticsSnapshotWithoutChangingRoutes() {
        val target = instrumentation.targetContext
        runBlocking { (target.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        val items = target.assets.open("pushContentD1Json.json").bufferedReader().use {
            JsonParser.parseString(it.readText()).asJsonObject.getAsJsonArray("contents")
        }
        val base = Gson().fromJson(items[0], Content::class.java)
        val directory = File(target.getExternalFilesDir(null), "day-pool-notifications").apply { mkdirs() }
        for (tag in listOf("en", "pt-BR", "es-MX", "es-ES", "id-ID", "hi-IN", "ja-JP", "ko-KR")) {
            // 选池/翻译规则由 notification 单测覆盖；这里验证既有构建器完整接收三字段。
            val translation = base.translations[tag]
            val content = if (translation == null) base else base.copy(title = translation.title,
                desc = translation.desc, buttonText = translation.buttonText)
            val model = GeneralModelManager().build(target, content, CheckCtrl.NotificationType.FCM)
            assertEquals(content.title, model.contentTitle)
            assertEquals(content.desc, model.contentContent)
            assertEquals(base.destination, content.destination)
            for (scale in listOf(1f, 2f)) {
                lateinit var bitmap: Bitmap
                instrumentation.runOnMainSync {
                    val configuration = Configuration(target.resources.configuration).apply {
                        densityDpi = 320; screenWidthDp = 360; fontScale = scale
                        setLocale(Locale.forLanguageTag(tag))
                    }
                    val context = ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
                    val parent = FrameLayout(context)
                    val view = requireNotNull(model.bigContentView).apply(context, parent)
                    parent.addView(view)
                    parent.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
                    assertEquals(content.title, view.findViewById<TextView>(io.docview.push.R.id.tvTitle).text.toString())
                    assertEquals(content.desc, view.findViewById<TextView>(io.docview.push.R.id.tvDesc).text.toString())
                    assertEquals(content.buttonText, view.findViewById<TextView>(io.docview.push.R.id.tvAction).text.toString())
                    val action = view.findViewById<TextView>(io.docview.push.R.id.tvAction)
                    assertTrue("$tag/$scale: action text must fit", action.layout.height <= action.height - action.paddingTop - action.paddingBottom)
                    bitmap = Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE) // 模拟系统通知容器的浅色背景。
                    parent.draw(canvas)
                }
                File(directory, "${tag}_$scale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
