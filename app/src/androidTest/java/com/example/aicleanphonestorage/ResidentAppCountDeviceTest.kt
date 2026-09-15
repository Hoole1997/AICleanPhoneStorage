package com.example.aicleanphonestorage

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import com.example.aicleanphonestorage.core.data.apps.InstalledAppCountRepository
import com.example.aicleanphonestorage.feature.home.data.*
import com.example.aicleanphonestorage.feature.push.CleanNotificationHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 仅核对应用数量数据与共享快照；不改变真实归因、不测试通知视图或扫描/删除文件。 */
@RunWith(AndroidJUnit4::class)
class ResidentAppCountDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun reportedAppCount(host: CleanNotificationHost): Int =
        host.residentContent().text.split(" / ")[1].substringAfterLast(' ')
            .filter { Character.isDigit(it) }.map { Character.digit(it, 10) }.joinToString("").toInt()

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
