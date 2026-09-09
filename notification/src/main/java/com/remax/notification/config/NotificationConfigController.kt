package com.remax.notification.config

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONObject

/** assets 只在模块的 IO 消费线程加载一次；无广告归因或原项目 base 单例依赖。 */
internal class NotificationConfigController(private val context: Context) {
    val config: PushConfig by lazy {
        try {
            // 已激活的配置来自 SDK 本地缓存；网络请求不会阻塞本次通知。
            val remote = FirebaseRemoteConfig.getInstance().getString("pushConfigJson")
            val remoteRoot = try {
                if (remote.length in 1..32_768) JSONObject(remote).optJSONObject("organic_channel") else null
            } catch (_: org.json.JSONException) { null }
            val root = remoteRoot ?: context.assets.open("push_config.json").bufferedReader().use {
                JSONObject(it.readText()).getJSONObject("organic_channel")
            }
            PushConfig(
                totalPushCount = root.optInt("total_push_count", 3).coerceIn(0, 20),
                unlockPushInterval = root.optInt("unlock_push_interval", 10).coerceAtLeast(1),
                backgroundPushInterval = root.optInt("background_push_interval", 10).coerceAtLeast(1),
                newUserCooldown = root.optInt("new_user_cooldown", 24).coerceAtLeast(0),
                doNotDisturbStart = root.optString("do_not_disturb_start", "02:00"),
                doNotDisturbEnd = root.optString("do_not_disturb_end", "08:00"),
                notificationEnabled = root.optInt("notification_enabled", 1) == 1,
            )
        } catch (_: java.io.IOException) {
            PushConfig(notificationEnabled = false)
        } catch (_: org.json.JSONException) {
            PushConfig(notificationEnabled = false)
        }
    }

    /** 每次进程启动至多请求一次，SDK 另限制 12 小时间隔；新值下一进程使用，避免中途改变额度。 */
    fun fetchForNextStart() {
        val remote = FirebaseRemoteConfig.getInstance()
        remote.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(10).setMinimumFetchIntervalInSeconds(43_200).build())
            .continueWithTask { remote.fetchAndActivate() }
            .addOnFailureListener {
                android.util.Log.w("CleanPush", "Remote policy unavailable; keeping local policy")
            }
    }
}
