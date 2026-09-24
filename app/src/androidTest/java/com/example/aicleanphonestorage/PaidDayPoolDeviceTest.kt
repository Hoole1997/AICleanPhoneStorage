package com.example.aicleanphonestorage

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicleanphonestorage.app.CleanApplication
import io.docview.push.builder.GeneralNotificationData
import io.docview.push.check.CheckCtrl
import io.docview.push.config.ContentController
import io.docview.push.controller.TriggerCtrl
import kotlinx.coroutines.*
import net.corekit.core.controller.ChannelUserController
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 只切换通知模块的测试快照，结束后恢复 SDK 实际渠道；不修改真实归因或推进买量文案游标。 */
@RunWith(AndroidJUnit4::class)
class PaidDayPoolDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runtime = (context.applicationContext as CleanApplication).notificationRuntime
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test fun naturalUsersCannotStartOrConsumePoolsAndLatePaidConfirmationEnablesThem() = runBlocking {
        runtime.awaitReady()
        try {
            runtime.setPaidUser(false)
            val cursors = File(context.noBackupFilesDir, "push_day_content/cursors.properties")
            val before = withContext(Dispatchers.IO) { if (cursors.exists()) cursors.readBytes() else null }
            withContext(Dispatchers.IO) {
                assertFalse(ContentController.initialize(context))
                assertFalse(ContentController.isInitialized())
                repeat(3) { assertNull(ContentController.getNextContent()) }
                assertArrayEquals(before, if (cursors.exists()) cursors.readBytes() else null)
            }
            runtime.setPaidUser(true)
            withTimeout(5000) { while (!ContentController.isInitialized()) delay(20) }
            assertTrue(ContentController.isInitialized())
            runtime.setPaidUser(false)
            assertFalse(ContentController.isInitialized())
            assertNull(ContentController.getNextContent())
        } finally { restoreActualChannel() }
    }

    @Test fun naturalDirectTriggersDoNotPublishOrInvokeSuccessCallbacks() = runBlocking {
        runtime.awaitReady()
        try {
            runtime.setPaidUser(false)
            val published = AtomicInteger()
            for (type in listOf(CheckCtrl.NotificationType.UNLOCK, CheckCtrl.NotificationType.BACKGROUND,
                CheckCtrl.NotificationType.KEEPALIVE, CheckCtrl.NotificationType.FCM)) {
                TriggerCtrl.triggerGeneralNotification(type) { published.incrementAndGet() }
            }
            // 同步准入在分配发布任务之前返回；游标和文案池也保持不可用。
            assertEquals(0, published.get())
            assertNull(ContentController.getNextContent())
            assertFalse(ContentController.isInitialized())
        } finally { restoreActualChannel() }
    }

    @Test fun queuedPaidNotificationIsRejectedWhenAudienceChangesBeforePublication() = runBlocking {
        runtime.awaitReady()
        assumeTrue(manager.areNotificationsEnabled())
        val id = 981735
        val release = CompletableDeferred<Unit>()
        try {
            runtime.setPaidUser(true)
            val residentWasPresent = manager.activeNotifications.any { it.id == TriggerCtrl.getResidentNotificationId() }
            val data = GeneralNotificationData(id, "Notification test", "Audience gate test")
            val published = CompletableDeferred<Unit>()
            dispatch({ data }) { _, _ -> published.complete(Unit) }
            withTimeout(5000) { published.await() }
            assertTrue(manager.activeNotifications.any { it.id == id })
            manager.cancel(id)

            val entered = CompletableDeferred<Unit>()
            val lateSuccess = AtomicInteger()
            dispatch({ entered.complete(Unit); release.await(); data }) { _, _ -> lateSuccess.incrementAndGet() }
            withTimeout(5000) { entered.await() }
            val scopeField = TriggerCtrl::class.java.getDeclaredField("notificationScope").apply { isAccessible = true }
            val scope = scopeField.get(TriggerCtrl) as CoroutineScope
            val queued = scope.coroutineContext[Job]!!.children.toList()
            runtime.setPaidUser(false)
            release.complete(Unit)
            withTimeout(5000) { queued.forEach { it.join() } }
            assertEquals(0, lateSuccess.get())
            assertFalse(manager.activeNotifications.any { it.id == id })
            if (residentWasPresent) assertTrue(manager.activeNotifications.any { it.id == TriggerCtrl.getResidentNotificationId() })
        } finally {
            release.complete(Unit)
            manager.cancel(id)
            restoreActualChannel()
        }
    }

    private fun dispatch(build: suspend (Context) -> GeneralNotificationData,
        sent: (GeneralNotificationData, Notification) -> Unit) {
        // 覆盖生产发布边界，不为测试新增对外发布 API；模型是独立样例，不消费轮播游标。
        val method = TriggerCtrl::class.java.getDeclaredMethod("triggerNotification", String::class.java,
            kotlin.jvm.functions.Function2::class.java, kotlin.jvm.functions.Function1::class.java,
            kotlin.jvm.functions.Function2::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val notification: (GeneralNotificationData) -> Notification = { model ->
            NotificationCompat.Builder(context, TriggerCtrl.CHANNEL_ID_GENERAL_SILENT)
                .setSmallIcon(R.drawable.ic_home_clean).setContentTitle(model.contentTitle)
                .setContentText(model.contentContent).setSilent(true).build()
        }
        method.invoke(TriggerCtrl, "测试分池", build, notification, sent, true)
    }

    private suspend fun restoreActualChannel() {
        val paid = withContext(Dispatchers.IO) {
            ChannelUserController.getCurrentChannel() == ChannelUserController.UserChannelType.PAID
        }
        runtime.setPaidUser(paid)
    }
}
