package com.example.aicleanphonestorage

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.data.apps.InstalledAppCountRepository
import com.example.aicleanphonestorage.feature.home.data.*
import com.example.aicleanphonestorage.feature.push.*
import io.docview.push.NotificationDestination
import io.docview.push.analytics.NotificationContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 核对共享数量、通知格式化与点击快照传递；测试数据只注入独立 Host，不改变真实归因。 */
@RunWith(AndroidJUnit4::class)
class ResidentAppCountDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun reportedAppCount(host: CleanNotificationHost): Int =
        host.residentContent().text.split(" / ")[1].substringAfterLast(' ')
            .filter { Character.isDigit(it) }.map { Character.digit(it, 10) }.joinToString("").toInt()

    @Test fun clickCarriesNumericSnapshotForEveryEntryAndConsumesItOnce() {
        for (entry in listOf("clean", "app", "photos", "accelerate")) {
            for (count in listOf(0, 12, 1234)) {
                val original = NotificationNavigation.residentIntent(context, NotificationDestination.APP_MANAGER,
                    entry, "shown", count, NotificationContent("title", "text"))
                // Intent 经系统 parcel 传递后仍为数值；后续通知使用不同数量不覆盖旧点击。
                val parcel = android.os.Parcel.obtain()
                val delivered = try {
                    original.writeToParcel(parcel, 0)
                    parcel.setDataPosition(0)
                    Intent.CREATOR.createFromParcel(parcel)
                } finally { parcel.recycle() }
                val updated = NotificationNavigation.residentIntent(context, NotificationDestination.APP_MANAGER,
                    entry, "shown", count + 1, NotificationContent("title", "text"))
                assertFalse(original.filterEquals(updated))
                assertEquals(mapOf("entry" to entry, "clean_badge" to "shown", "app_badge_count" to count),
                    ResidentClickTelemetry.take(delivered))
                assertFalse(delivered.hasExtra(ResidentClickTelemetry.APP_BADGE_COUNT))
                assertNull(ResidentClickTelemetry.take(delivered))
            }
        }
    }

    @Test fun notificationFormatsOneSharedSnapshotInLocalizedRemoteViews() {
        var reads = 0
        val host = CleanNotificationHost(context, HomeCleaningState(true, generate = { 6799 }),
            appCount = { reads++; 1234 })
        val localized = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("ar"))
        })
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = host.residentViews(false, localized).apply(localized, null)
            val badge = root.findViewById<android.widget.TextView>(R.id.shortcut_network_badge)
            val expected = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ar")).apply {
                isGroupingUsed = false
            }.format(1234)
            assertEquals(expected, badge.text.toString())
            assertEquals(android.view.View.VISIBLE, badge.visibility)
        }
        assertEquals(1, reads)
    }

    @Test fun unavailableCountIsNotReportedAsZeroAndOrdinaryPushIsIgnored() {
        for (count in listOf(null, -1)) {
            val intent = NotificationNavigation.residentIntent(context, NotificationDestination.CLEAN,
                "clean", "none", count, NotificationContent("", ""))
            assertEquals(mapOf("entry" to "clean", "clean_badge" to "none"), ResidentClickTelemetry.take(intent))
            assertFalse(intent.hasExtra(ResidentClickTelemetry.APP_BADGE_COUNT))
        }
        assertNull(ResidentClickTelemetry.take(Intent().putExtra(ResidentClickTelemetry.APP_BADGE_COUNT, 12)))
    }

    @Test fun productionCountUsesTheSameLauncherDefinitionAsHome() = runBlocking<Unit> {
        val app = context.applicationContext as CleanApplication
        val repository = app.container.installedAppCount
        val (expected, oldInstalledCount) = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val launcher = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
            ).map { it.activityInfo.packageName }.toSet().size
            @Suppress("DEPRECATION")
            val installed = context.packageManager.getInstalledApplications(0).size
            launcher to installed
        }
        assertEquals(expected, repository.refresh())
        val overview = withTimeout(15_000) {
            app.container.homeOverviewRepository.observeOverview().first { it.tools.apps !is HomeToolMetric.Reading }
        }
        if (overview.tools.apps is HomeToolMetric.AppCount)
            assertEquals(repository.count.value, (overview.tools.apps as HomeToolMetric.AppCount).value)
        val host = CleanNotificationHost(context, HomeCleaningState(true, generate = { 6799 }),
            appCount = { repository.count.value })
        assertEquals(expected, reportedAppCount(host))
        Log.i("ResidentAppCountTest", "oldInstalled=$oldInstalledCount sharedLauncher=$expected home=${overview.tools.apps}")
    }

    @Test fun notificationReadsCurrentSharedValueEvenOnMainThread() = runBlocking<Unit> {
        var source = 12
        val repository = InstalledAppCountRepository { source }
        val host = CleanNotificationHost(context, HomeCleaningState(true, generate = { 6799 }),
            appCount = { repository.count.value })
        repository.refresh()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            assertEquals(12, reportedAppCount(host))
        }
        source = 13
        repository.refresh()
        instrumentation.runOnMainSync {
            assertEquals(13, reportedAppCount(host))
        }
    }
}
