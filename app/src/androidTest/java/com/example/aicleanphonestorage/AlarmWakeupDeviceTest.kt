package com.example.aicleanphonestorage

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.docview.push.alarm.AlarmWakeupPort
import io.docview.push.alarm.AlarmWakeupReceiver
import io.docview.push.alarm.AlarmWakeupRegistration
import io.docview.push.builder.GeneralModelManager
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用独立 Request Code 验证系统注册，不修改真实配置、生产 Alarm 或发送测试通知。 */
@RunWith(AndroidJUnit4::class)
class AlarmWakeupDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun packagedDefaultsEnablePaidOnlyForFifteenMinutes() {
        // 从实际安装 APK 读取资源，防止本地模板存在但没有打入 notification/app 的资产。
        val json = instrumentation.targetContext.assets.open("alarm_wakeup_config.json").bufferedReader().use { it.readText() }
        val config = io.docview.push.alarm.parseAlarmWakeupConfig(json)
        assertTrue(config.enabled)
        assertEquals(io.docview.push.alarm.AlarmWakeupPolicy(true, 15), config.policyFor(confirmedPaid = true))
        assertEquals(io.docview.push.alarm.AlarmWakeupPolicy(false, 15), config.policyFor(confirmedPaid = false))
    }

    @Test fun inexactSystemAlarmReusesOneImmutableIntentAndCancelsIt() {
        val context = instrumentation.targetContext
        val requestCode = 84103
        val port = Class.forName("io.docview.push.alarm.AndroidAlarmWakeup")
            .getConstructor(Context::class.java, Int::class.javaPrimitiveType)
            .newInstance(context, requestCode) as AlarmWakeupPort
        fun pending() = PendingIntent.getBroadcast(context, requestCode,
            Intent(context, AlarmWakeupReceiver::class.java).setAction("io.docview.push.ALARM_WAKEUP"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        try {
            port.cancel()
            assertFalse(port.exists())
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
            port.schedule(AlarmWakeupRegistration("device-fixture-1", boot, SystemClock.elapsedRealtime() + 900_000L))
            val first = requireNotNull(pending())
            assertTrue(port.exists())
            if (android.os.Build.VERSION.SDK_INT >= 31) assertTrue(first.isImmutable)
            port.schedule(AlarmWakeupRegistration("device-fixture-2", boot, SystemClock.elapsedRealtime() + 1_800_000L))
            assertEquals(first, pending())
            port.cancel()
            assertFalse(port.exists())
            assertNull(pending())
        } finally { port.cancel() }
    }

    @Test fun alarmNotificationKeepsNativeTextAndActionAtLargeFont() {
        val target = instrumentation.targetContext
        val content = target.assets.open("pushContentD1Json.json").bufferedReader().use {
            Gson().fromJson(JsonParser.parseString(it.readText()).asJsonObject.getAsJsonArray("contents")[0], Content::class.java)
        }
        val configuration = android.content.res.Configuration(target.resources.configuration).apply {
            densityDpi = 320; screenWidthDp = 360; fontScale = 2f
            setLocale(java.util.Locale.ENGLISH)
        }
        val context = android.view.ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_AICleanPhoneStorage)
        val model = GeneralModelManager().build(target, content, CheckCtrl.NotificationType.ALARM_MANAGER)
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val parent = FrameLayout(context)
            val view = requireNotNull(model.bigContentView).apply(context, parent)
            parent.addView(view)
            parent.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
            assertEquals(content.title, view.findViewById<TextView>(io.docview.push.R.id.tvTitle).text.toString())
            val action = view.findViewById<TextView>(io.docview.push.R.id.tvAction)
            assertEquals(content.buttonText, action.text.toString())
            assertTrue(action.layout.height <= action.height - action.paddingTop - action.paddingBottom)
            bitmap = Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
            parent.draw(canvas)
        }
        try {
            val file = File(target.getExternalFilesDir(null), "alarm-notification-large-font.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
