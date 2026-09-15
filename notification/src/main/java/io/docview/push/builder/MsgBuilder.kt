package io.docview.push.builder

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.blankj.utilcode.util.StringUtils
import com.google.gson.Gson
import io.docview.push.utils.ViewBuilder
import io.docview.push.R
import io.docview.push.check.CheckCtrl
import io.docview.push.config.Content
import io.docview.push.earthquake.EARTHQUAKE_NOTIFICATION_ACTION
import io.docview.push.config.ContentController
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.earthquake.EarthquakeInfo
import io.docview.push.service.KeepAliveServiceManager
import io.docview.push.host.PushLanguage
import java.time.LocalDate
import kotlin.random.Random


enum class NotificationType {
    GENERAL,
    JUNK, PROCESS, BOOKMARK,MAIN,SCAN,DUPLICATE,SIMILAR,SPEED,
    EARTHQUAKE
}

val type2notificationId = mapOf(
    NotificationType.GENERAL to 10000,
    NotificationType.JUNK to 10001,
    NotificationType.PROCESS to 10003,
    NotificationType.BOOKMARK to 10004,
    NotificationType.EARTHQUAKE to 10005,
    NotificationType.DUPLICATE to 10006,
    NotificationType.SIMILAR to 10007,
    NotificationType.SPEED to 10008
)

val LANDING_NOTIFICATION_ID = "landing_notification_id"
val LANDING_NOTIFICATION_ACTION = "landing_notification_action"
val LANDING_NOTIFICATION_FROM = "landing_notification_from"
val LANDING_NOTIFICATION_TITLE = "landing_notification_title"
val LANDING_NOTIFICATION_CONTENT = "landing_notification_content"
val LANDING_NOTIFICATION_EARTHQUAKE_DATA = "landing_notification_earthquake_data"

/**
 * 通知数据对象
 */
class GeneralNotificationData(
    val notificationId: Int,
    val contentTitle: String,
    val contentContent: String,
    val contentIntent: PendingIntent? = null,
    val contentView: RemoteViews? = null,
    val bigContentView: RemoteViews? = null,
)

fun entryPointPendingIntent(
    context: Context,
    notificationId: Int,
    applyIntent: ((Intent) -> Unit)? = null,
): PendingIntent {
    val intent = entryPointIntent(context)
    intent.putExtra(LANDING_NOTIFICATION_ID, notificationId)
    applyIntent?.invoke(intent)
    // 同一普通通知槽位也按业务动作区分 PendingIntent，旧卡片不会被后续另一业务覆盖路由。
    intent.action = "${context.packageName}.push.$notificationId.${intent.getIntExtra(LANDING_NOTIFICATION_ACTION, Content.TYPE_HOME)}"


    return PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        null
    )
}

fun entryPointIntent(context: Context): Intent =
    requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)).apply {
        action = "io.docview.push.ACTION_OPEN_APP"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra("from_notification", true)
        putExtra("notification_timestamp", System.currentTimeMillis())
    }

/**
 * 重置红点显示状态（用于测试或特殊情况）
 * @param context 上下文
 */
fun resetRedPointStatus(context: Context) {
    try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove("has_clicked_today")
            .remove("last_click_date")
            .apply()
    } catch (e: Exception) {
        // 忽略异常
    }
}

/**
 * 检查是否应该显示红点（点击后才隐藏）
 * @param context 上下文
 * @return true 如果应该显示红点，false 如果不应该显示
 */
private fun shouldShowRedPoint(context: Context): Boolean {
    return try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        val hasClickedToday = sharedPreferences.getBoolean("has_clicked_today", false)
        val lastClickDate = sharedPreferences.getString("last_click_date", "")
        val today = LocalDate.now().toString()

        // 如果是新的一天，重置点击状态
        if (lastClickDate != today) {
            sharedPreferences.edit()
                .putBoolean("has_clicked_today", false)
                .putString("last_click_date", today)
                .apply()
            return true // 新的一天显示红点
        }

        // 如果今天还没点击过，显示红点
        !hasClickedToday
    } catch (e: Exception) {
        // 异常情况下默认显示红点
        true
    }
}

/**
 * 标记红点已点击（隐藏红点）
 * @param context 上下文
 */
fun markRedPointClicked(context: Context) {
    try {
        val sharedPreferences =
            context.getSharedPreferences("notification_red_point", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        sharedPreferences.edit()
            .putBoolean("has_clicked_today", true)
            .putString("last_click_date", today)
            .apply()

        KeepAliveServiceManager.startKeepAliveService(context)
    } catch (e: Exception) {
        // 忽略异常
    }
}

class EarthquakeModelManager() {
    private val gson = Gson()

    fun getModel(context: Context, earthquake:EarthquakeInfo,type:CheckCtrl.NotificationType): GeneralNotificationData {
        val notificationId = type2notificationId[NotificationType.GENERAL] ?: 0
        val content = earthquake.place.orEmpty()
        val curLang = PushLanguage.getInstance().getAliens()
        val pushText = EarthquakeController.earthquakePushMessageMap.getOrDefault(curLang,EarthquakeController.earthquakePushMessageMap.values.first())
        val title = pushText.format(earthquake.magnitude.toString())

        // 将地震信息序列化为JSON字符串
        val earthquakeJson = gson.toJson(earthquake)

        val pendingIntent = entryPointPendingIntent(context, notificationId) {
            it.putExtra(LANDING_NOTIFICATION_ACTION,EARTHQUAKE_NOTIFICATION_ACTION)
            it.putExtra(LANDING_NOTIFICATION_FROM, type.string)
            it.putExtra(LANDING_NOTIFICATION_TITLE, title)
            it.putExtra(LANDING_NOTIFICATION_CONTENT, content)
            it.putExtra(LANDING_NOTIFICATION_EARTHQUAKE_DATA, earthquakeJson)
        }


        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_earthquake_12,
            R.layout.layout_notification_earthquake
        )
            .setTextViewText(R.id.title, title)
            .setTextViewText(R.id.time, earthquake.shortTime)
            .setTextViewText(R.id.info, StringUtils.getString(R.string.noti_earthquake_click_text))
            .build()


        return GeneralNotificationData(
            notificationId = notificationId,
            contentTitle = title,
            contentContent = content,
            contentIntent = pendingIntent,
            contentView = contentView,
            bigContentView = contentView
        )
    }

}

class GeneralModelManager() {

    fun getModel(context: Context,type:CheckCtrl.NotificationType): GeneralNotificationData {
        val data = requireNotNull(ContentController.getNextContent()) { "Notification content is not initialized" }
        return build(context, data, type)
    }

    /** 内容选择与构建分离，确保本地/远程内容经过相同的图标、文案、PendingIntent 链路。 */
    fun build(context: Context, data: Content, type: CheckCtrl.NotificationType): GeneralNotificationData {
        val notificationId = type2notificationId[NotificationType.GENERAL] ?: 0
        val title = data.title
        val content = data.desc
        val pendingIntent = entryPointPendingIntent(context, notificationId) {
            it.putExtra(LANDING_NOTIFICATION_ACTION, data.destination.contentType)
            it.putExtra(LANDING_NOTIFICATION_FROM, type.string)
            it.putExtra(LANDING_NOTIFICATION_TITLE, title)
            it.putExtra(LANDING_NOTIFICATION_CONTENT, content)
        }

        // 清理数量必须来自真实结果，不能使用来源浏览器的随机角标。
        val badgeCount = ""
        val contentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_general_12,
            R.layout.layout_notification_general
        )
            .setImageViewResource(R.id.iv, getIcon(data))
            .setTextViewText(R.id.tvCount, badgeCount)
            .setViewVisibility(R.id.tvCount, android.view.View.GONE)
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .setTextViewText(R.id.tvAction, data.buttonText)
            .setOnClickPendingIntent(R.id.tvAction, pendingIntent)
            .build()

        val bigContentView = ViewBuilder(
            context.packageName,
            R.layout.layout_notification_general_big_12,
            R.layout.layout_notification_general_big
        )
            .setImageViewResource(R.id.iv, getIcon(data))
            .setTextViewText(R.id.tvCount, badgeCount)
            .setViewVisibility(R.id.tvCount, android.view.View.GONE)
            .setTextViewText(R.id.tvTitle, title)
            .setTextViewText(R.id.tvDesc, content)
            .setTextViewText(R.id.tvAction, data.buttonText)
            .setOnClickPendingIntent(R.id.tvAction, pendingIntent)
            .build()

        return GeneralNotificationData(
            notificationId = notificationId,
            contentTitle = title,
            contentContent = content,
            contentIntent = pendingIntent,
            contentView = contentView,
            bigContentView = bigContentView
        )
    }

    private fun getIcon(data: Content): Int = io.docview.push.host.PushEnvironment.host.contentIcon(data.iconDestination)

}

/** 使用已实现的清理/流量/照片/闲置入口，保留来源控制器调用约定。 */
class ResidentModelManger {
    fun getModel(context: Context): GeneralNotificationData {
        val host = io.docview.push.host.PushEnvironment.host
        val content = host.residentContent().bounded()
        return GeneralNotificationData(
            notificationId = type2notificationId[NotificationType.JUNK]!!,
            contentTitle = content.title,
            contentContent = content.text,
            contentIntent = host.contentIntent(io.docview.push.NotificationDestination.HOME),
            contentView = host.residentViews(true),
            bigContentView = host.residentViews(false),
        )
    }
}
