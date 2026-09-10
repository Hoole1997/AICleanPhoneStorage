package com.example.aicleanphonestorage

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.MainActivity
import com.example.aicleanphonestorage.feature.push.NotificationNavigation
import com.example.aicleanphonestorage.feature.startup.StartupActivity
import io.docview.push.NotificationDestination
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 真机验证冷启动、已有首页时的通知、升级前 PendingIntent，以及启动页退出后的栈。 */
@RunWith(AndroidJUnit4::class)
class StartupActivityFlowTest {
    @Test fun coldWarmAndLegacyNotificationClicksPassThroughStartupThenHome() {
        // 本用例会展示真实开屏广告；普通自动回归使用 StartupAdCoordinatorTest 的假请求。
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_live_ads") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val app = context.applicationContext as Application
        val trace = ResumeTrace()
        instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(trace) }
        try {
            val intent = Intent(context, StartupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<StartupActivity>(intent).use {
                assertStartupThenHome(trace)
                trace.resumed.clear()
                NotificationNavigation.pendingIntent(context, NotificationDestination.HOME).send()
                assertStartupThenHome(trace)
                trace.resumed.clear()
                val old = PendingIntent.getActivity(context, 99101,
                    Intent(context, MainActivity::class.java)
                        .putExtra(NotificationNavigation.EXTRA_DESTINATION, "home")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try { old.send(); assertStartupThenHome(trace) } finally { old.cancel() }
                // 系统 RecentTaskInfo 在转场期间可能仍是旧快照，按真实销毁回调验证启动页已退出。
                val deadline = SystemClock.elapsedRealtime() + 2_000
                while (trace.destroyed.count { it == StartupActivity::class.java.name } < 3 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                assertEquals(3, trace.destroyed.count { it == StartupActivity::class.java.name })
            }
        } finally {
            instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(trace) }
        }
    }

    private fun assertStartupThenHome(trace: ResumeTrace) {
        // 真实广告需要人工关闭；这里只限制手动测试等待，不影响产品的广告回调时机。
        val deadline = SystemClock.elapsedRealtime() + 120_000
        // 旧 PendingIntent 会先短暂恢复已有 Main，再转 Startup；等待完整的有序链路。
        fun completed(): Boolean {
            val startup = trace.resumed.indexOf(StartupActivity::class.java.name)
            return startup >= 0 && trace.resumed.lastIndexOf(MainActivity::class.java.name) > startup
        }
        while (!completed() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        val startup = trace.resumed.indexOf(StartupActivity::class.java.name)
        val home = trace.resumed.lastIndexOf(MainActivity::class.java.name)
        assertTrue("Expected startup then home, got ${trace.resumed}", startup >= 0 && home > startup)
    }

    private class ResumeTrace : Application.ActivityLifecycleCallbacks {
        val resumed = CopyOnWriteArrayList<String>()
        val destroyed = CopyOnWriteArrayList<String>()
        override fun onActivityResumed(activity: Activity) { resumed += activity.javaClass.name }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) { destroyed += activity.javaClass.name }
    }
}
