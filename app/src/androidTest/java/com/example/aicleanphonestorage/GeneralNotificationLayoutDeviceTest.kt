package com.example.aicleanphonestorage

import android.app.Notification
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import io.docview.push.builder.GeneralModelManager
import io.docview.push.builder.GeneralNotificationData
import io.docview.push.controller.TriggerCtrl
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实系统渲染折叠/展开/悬浮模板；只构建测试卡片，不发布、不更改语言或归因。 */
@RunWith(AndroidJUnit4::class)
class GeneralNotificationLayoutDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    @Test fun titleRemainsVisibleAcrossNotificationStatesAndFontSizes() {
        runBlocking { (target.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        val samples = mapOf(
            "en" to ("Review your device storage" to "Open the app to review files and choose what to keep."),
            "de" to ("Überprüfe den Speicherplatz deines Geräts" to "Öffne die App, um deine Dateien zu überprüfen und auszuwählen."),
            "ar" to ("راجع مساحة التخزين على جهازك" to "افتح التطبيق لمراجعة الملفات واختيار ما تريد الاحتفاظ به."),
            "hi" to ("अपने डिवाइस का स्टोरेज देखें" to "फ़ाइलों की समीक्षा करने और रखने के लिए ऐप खोलें।"),
        )
        val output = File(target.getExternalFilesDir(null), "general-notification-layout").apply { mkdirs() }
        for ((tag, texts) in samples) for (scale in listOf(1f, 1.3f)) for (night in listOf(false, true)) {
            val context = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
                densityDpi = 320
                fontScale = scale
                setLocale(Locale.forLanguageTag(tag))
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            })
            val content = Content("layout-test", texts.first, texts.second, texts.first, Content.TYPE_CLEAN, Content.TYPE_CLEAN)
            val model = GeneralModelManager().build(context, content, CheckCtrl.NotificationType.FCM)
            val notification = buildNotification(model)
            assertEquals(texts.first, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(texts.second, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(model.contentIntent, notification.contentIntent)
            instrumentation.runOnMainSync {
                // 直接验证自定义内容预算；系统模板则由平台管理大字体的布局。
                for (width in listOf(240, 320)) {
                    for ((remote, budget) in listOf(model.contentView to 48, model.bigContentView to 252)) {
                        val root = requireNotNull(remote).apply(context, null)
                        layout(root, width * 2)
                        val title = root.findViewById<TextView>(io.docview.push.R.id.tvTitle)
                        assertVisibleTitle(title, texts.first)
                        val button = root.findViewById<TextView>(io.docview.push.R.id.tvAction)
                        assertTrue("Both collapsed and expanded must retain the button", button.isShownInTree())
                        assertTrue(button.width > 0)
                        assertTrue(button.hasOnClickListeners())
                        if (remote == model.contentView && context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR)
                            assertTrue("Collapsed button stays to the right of the title", button.left + (button.parent as View).left > title.left + (title.parent as View).left)
                        assertTrue("$tag/$scale/$width: ${root.height} exceeds ${budget * 2}, title=${title.height}, button=${button.height}/${button.textSize}, desc=${root.findViewById<TextView>(io.docview.push.R.id.tvDesc).height}", root.height <= budget * 2)
                    }
                }
                val recovered = Notification.Builder.recoverBuilder(context, notification)
                val states = listOf("collapsed" to recovered.createContentView(),
                    "expanded" to recovered.createBigContentView(), "heads_up" to recovered.createHeadsUpContentView())
                for ((state, remote) in states) {
                    val root = requireNotNull(remote).apply(context, null)
                    // 系统模板的加权列需要 SystemUI 提供有界高度；UNSPECIFIED 会把正文测成 0。
                    layout(root, 640, (if (state == "expanded") 320 else 160) * 2)
                    val title = allText(root).firstOrNull { it.text.toString() == texts.first && it.isShownInTree() }
                    assertNotNull("$tag/$scale/$night/$state: visible title", title)
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(if (night) android.graphics.Color.DKGRAY else android.graphics.Color.WHITE)
                        root.draw(canvas)
                        File(output, "${tag}_${scale}_${night}_$state.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { bitmap.recycle() }
                    assertVisibleTitle(requireNotNull(title), texts.first)
                }
            }
        }
    }

    @Test fun systemUiPreview() {
        // 只在只读测试模拟器显式启用，普通设备回归不发布样例通知。
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("notificationPreview") == "true")
        runBlocking { (target.applicationContext as CleanApplication).notificationRuntime.awaitReady() }
        val manager = target.getSystemService(android.app.NotificationManager::class.java)
        val content = Content("preview", "Review your device storage",
            "Open the app to review files and choose what to keep.", "Open", Content.TYPE_CLEAN, Content.TYPE_CLEAN)
        val model = GeneralModelManager().build(target, content, CheckCtrl.NotificationType.FCM)
        val notification = buildNotification(model)
        val ui = instrumentation.uiAutomation
        val id = 981734
        try {
            manager.notify(id, notification)
            ui.executeShellCommand("cmd statusbar expand-notifications").close()
            android.os.SystemClock.sleep(2000) // 等待系统异步绑定 RemoteViews；最终截图由人工核验。
            assertTrue(manager.activeNotifications.any { it.id == id })
            fun capture(suffix: String) {
                val screenshot = requireNotNull(ui.takeScreenshot())
                try {
                    val directory = File(target.getExternalFilesDir(null), "general-notification-systemui").apply { mkdirs() }
                    val config = target.resources.configuration
                    File(directory, "${config.fontScale}_${config.uiMode}_$suffix.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { screenshot.recycle() }
            }
            capture("initial")
            val root = requireNotNull(ui.rootInActiveWindow)
            fun toggle(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                val description = node.contentDescription?.toString().orEmpty()
                if ((description == "Expand" || description == "Collapse") && node.isClickable)
                    return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    if (toggle(child)) return true
                }
                return false
            }
            if (toggle(root)) {
                android.os.SystemClock.sleep(500)
                capture("toggled")
            }
        } finally {
            manager.cancel(id)
            ui.executeShellCommand("cmd statusbar collapse").close()
        }
    }

    private fun buildNotification(model: GeneralNotificationData): Notification {
        // 验证生产构建分支，不为测试扩大控制器 API。
        val method = TriggerCtrl::class.java.getDeclaredMethod("buildGeneralNotification",
            GeneralNotificationData::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(TriggerCtrl, model, true) as Notification
    }

    private fun layout(view: View, width: Int, maxHeight: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight ?: 0,
                if (maxHeight == null) View.MeasureSpec.UNSPECIFIED else View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun View.isShownInTree() = generateSequence(this) { it.parent as? View }.all { it.visibility == View.VISIBLE }

    private fun assertVisibleTitle(view: TextView, expected: String) {
        assertEquals(expected, view.text.toString())
        assertTrue(view.isShownInTree())
        assertTrue(view.width > 0)
        val visibleLines = minOf(view.maxLines, view.layout.lineCount).coerceAtLeast(1)
        val required = view.layout.getLineBottom(visibleLines - 1) + view.paddingTop + view.paddingBottom
        assertTrue("title height=${view.height}, required=$required, layout=${view.layout.height}, maxLines=${view.maxLines}, lines=${view.lineCount}",
            view.height >= required)
    }

    private fun allText(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { allText(view.getChildAt(it)) }
        else -> emptyList()
    }
}
