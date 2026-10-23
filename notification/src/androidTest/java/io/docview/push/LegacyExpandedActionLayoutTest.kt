package io.docview.push

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.docview.push.scene.SceneRenderTestActivity
import java.io.File
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立测试 APK 渲染真实 RemoteViews，不发布通知或改动用户设备的语言设置。 */
@RunWith(AndroidJUnit4::class)
class LegacyExpandedActionLayoutTest {
    @Test fun actionTextAndTouchAreaStayCenteredAcrossExpandedLayouts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val output = File(target.getExternalFilesDir(null), "legacy-action-alignment").apply { mkdirs() }
        val labels = mapOf(
            "en" to "Check",
            "de" to "Dateien überprüfen und Speicherplatz freigeben",
            "ar" to "تحقق من الملفات وحرر مساحة التخزين",
            "hi" to "फ़ाइलें जाँचें और स्टोरेज खाली करें",
        )
        val click = PendingIntent.getActivity(target, 981735,
            Intent(target, SceneRenderTestActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        instrumentation.runOnMainSync {
            for ((tag, label) in labels) for (scale in listOf(1f, 1.3f, 2f)) {
                val context = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
                    densityDpi = 320
                    fontScale = scale
                    setLocale(Locale.forLanguageTag(tag))
                })
                for ((variant, resource) in listOf(
                    "legacy" to R.layout.layout_notification_general_big,
                    "modern" to R.layout.layout_notification_general_big_12,
                )) for (widthDp in listOf(240, 320)) for (animate in listOf(true, false)) {
                    val remote = RemoteViews(target.packageName, resource).apply {
                        setTextViewText(R.id.tvTitle, "Storage summary")
                        setTextViewText(R.id.tvDesc, "Review your files.")
                        setTextViewText(R.id.tvAction, label)
                        setOnClickPendingIntent(R.id.tvAction, click)
                        setViewVisibility(R.id.tvCount, View.GONE)
                        setViewVisibility(R.id.notificationButtonMotion, if (animate) View.VISIBLE else View.GONE)
                        setViewVisibility(R.id.notificationButtonStatic, if (animate) View.GONE else View.VISIBLE)
                    }
                    val root = remote.apply(context, null)
                    root.measure(View.MeasureSpec.makeMeasureSpec(widthDp * 2, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    val action = root.findViewById<TextView>(R.id.tvAction)
                    val surface = root.findViewById<View>(if (animate) R.id.notificationButtonMotion else R.id.notificationButtonStatic)
                    val key = "${variant}_${tag}_${scale}_${widthDp}_$animate"
                    // 先保存失败样本，便于比较真实字体度量和动效背景的边界。
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        root.draw(canvas)
                        File(output, "$key.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                    val textCenter = action.top + action.totalPaddingTop + action.layout.height / 2f
                    val surfaceCenter = surface.top + surface.height / 2f
                    assertTrue("$key: text center=$textCenter, background center=$surfaceCenter",
                        abs(textCenter - surfaceCenter) <= 1f)
                    assertEquals("$key: touch area must cover the button background", surface.height, action.height)
                    assertTrue("$key: text must not be clipped",
                        action.layout.height <= action.height - action.compoundPaddingTop - action.compoundPaddingBottom)
                    assertTrue("$key: button remains clickable", action.hasOnClickListeners())
                }
            }
        }
    }
}
