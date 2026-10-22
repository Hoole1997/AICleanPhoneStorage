package io.docview.push.check

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.docview.push.config.Config
import io.docview.push.config.ConfigCtrl
import io.docview.push.config.NotificationConfig
import io.docview.push.utils.ResetCtrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 真实 SharedPreferences + 旧准入入口；只修改独立测试 APK，不改系统时间、不发送通知。 */
@RunWith(AndroidJUnit4::class)
class DailyNotificationCountDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("reset_at_midnight_prefs", Context.MODE_PRIVATE)
    private val checker = CheckCtrl.getInstance()
    private val configField = ConfigCtrl::class.java.getDeclaredField("config").apply { isAccessible = true }
    private var previousConfig: Any? = null
    private val countKey = "current_notification_count"
    private val dateKey = "${countKey}_date"
    private val today get() = LocalDate.now().toString()

    @Before fun prepare() {
        check(context.packageName == "io.docview.push.test")
        prefs.edit().clear().commit()
        checker.initialize(context)
        previousConfig = configField.get(ConfigCtrl)
        // 关闭发送功能，跨日测试只验证 DAILY_LIMIT 不再拦截，不启动网络/广告/通知任务。
        val tier = Config(3, 10, 10, 0, 0, 0, "00:00", "00:00", 0, 15)
        configField.set(ConfigCtrl, NotificationConfig(tier, tier))
    }

    @After fun cleanup() {
        configField.set(ConfigCtrl, previousConfig)
        prefs.edit().clear().commit()
    }

    private fun seed(date: String?, count: Long = 3) {
        prefs.edit().putLong(countKey, count).apply {
            if (date == null) remove(dateKey) else putString(dateKey, date)
        }.commit()
    }

    @Test fun sameDayQuotaRemainsBlockedAndReadsDoNotResetIt() {
        seed(today)
        repeat(3) {
            assertEquals(BlockReason.DAILY_LIMIT_REACHED, checker.canTriggerNotificationWithReason(CheckCtrl.NotificationType.UNLOCK).second)
            assertEquals(3, checker.getTodayNotificationCount())
            assertEquals(0, checker.getRemainingNotificationCount())
        }
    }

    @Test fun eachLegacyTriggerResetsYesterdayBeforeCheckingQuotaWithoutTouchingOtherKeys() {
        val unrelated = "last_background_notification_time"
        prefs.edit().putLong(unrelated, 123L).putString("${unrelated}_date", "2001-01-01").commit()
        for (type in listOf(CheckCtrl.NotificationType.UNLOCK, CheckCtrl.NotificationType.BACKGROUND,
            CheckCtrl.NotificationType.KEEPALIVE, CheckCtrl.NotificationType.ALARM_MANAGER, CheckCtrl.NotificationType.FCM)) {
            seed(LocalDate.now().minusDays(1).toString())
            assertNotEquals(type.name, BlockReason.DAILY_LIMIT_REACHED, checker.canTriggerNotificationWithReason(type).second)
            assertEquals(0, checker.getTodayNotificationCount())
            assertEquals(today, prefs.getString(dateKey, null))
            assertEquals(3, checker.getRemainingNotificationCount())
            assertEquals(123L, prefs.getLong(unrelated, 0))
        }
    }

    @Test fun recordingAfterMidnightStartsAtOneWithoutRequiringAnotherEligibilityCheck() {
        seed(LocalDate.now().minusDays(1).toString(), 100)
        checker.incrementNotificationCount()
        assertEquals(1, checker.getTodayNotificationCount())
        assertEquals(today, prefs.getString(dateKey, null))
        checker.incrementNotificationCount()
        assertEquals(2, checker.getTodayNotificationCount())
    }

    @Test fun missingLegacyDateMigratesOnceAndPersistedDateSurvivesControllerRecreation() {
        seed(null, 9999)
        assertEquals(0, checker.getTodayNotificationCount())
        checker.incrementNotificationCount()
        val restored = ResetCtrl::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        restored.initialize(context)
        assertEquals(1, restored.getIntValue(countKey))
        seed(LocalDate.now().minusMonths(2).toString(), 9999)
        assertEquals(0, restored.getIntValue(countKey))
        assertEquals(today, prefs.getString(dateKey, null))
    }

    @Test fun manualResetAlsoEstablishesTodayBeforeFurtherCounting() {
        seed(LocalDate.now().minusDays(1).toString())
        checker.resetTodayNotificationCount()
        assertEquals(today, prefs.getString(dateKey, null))
        checker.incrementNotificationCount()
        assertEquals(1, checker.getTodayNotificationCount())
    }

    @Test fun concurrentIncrementsDoNotLoseCountsAfterDailyRollover() {
        seed(LocalDate.now().minusDays(1).toString(), 9999)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..4).map { Callable { repeat(50) { checker.incrementNotificationCount() } } }
            pool.invokeAll(tasks).forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(200, checker.getTodayNotificationCount())
            assertEquals(today, prefs.getString(dateKey, null))
        } finally { pool.shutdownNow() }
    }
}
